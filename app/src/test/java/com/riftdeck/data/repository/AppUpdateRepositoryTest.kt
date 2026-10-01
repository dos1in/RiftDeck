package com.riftdeck.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.riftdeck.core.update.ApkValidationException
import com.riftdeck.core.update.ApkValidationFailure
import com.riftdeck.core.update.ReleaseVersion
import com.riftdeck.core.update.UpdateRelease
import com.riftdeck.core.update.UpdateSource
import com.riftdeck.core.update.verifyUpdatePayload
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppUpdateRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val payload = "signed APK fixture stand-in".toByteArray()
    private val release = UpdateRelease("v0.1.1", "0.1.1", ReleaseVersion(0, 1, 1), "New fixes",
        "https://github.com/dos1in/RiftDeck/releases/tag/v0.1.1", "https://github.com/dos1in/RiftDeck/releases/download/v0.1.1/riftdeck.apk",
        payload.size.toLong(), sha(payload))
    private var clock = 1_000_000L

    @Test fun constructorDoesNotContactNetworkAndDisabledAutoCheckSurvivesRecreation(): Unit = runBlocking {
        val source = FakeSource()
        withRepository(source) { repository, preferences ->
            assertTrue(preferences.preferences.first().autoCheck)
            preferences.setAutoCheck(false)
            delay(80)
            repository.onForeground()
            delay(100)
            assertEquals(0, source.checks.get())
        }
        withRepository(source) { repository, _ ->
            repository.onForeground()
            delay(100)
            assertFalse(repository.state.value.autoCheck)
            assertEquals(0, source.checks.get())
            repository.check(manual = true)
            await(repository, UpdateStatus.Available)
            assertEquals(1, source.checks.get())
        }
    }

    @Test fun successfulChecksAreThrottledForOneDayButManualCheckBypassesThrottle(): Unit = runBlocking {
        val source = FakeSource()
        withRepository(source) { repository, _ ->
            repository.onForeground(); await(repository, UpdateStatus.Available)
            assertEquals(1, source.checks.get())
            clock += 23 * 60 * 60 * 1000L
            repository.onForeground(); delay(100)
            assertEquals(1, source.checks.get())
            repository.check(manual = true)
            withTimeout(3000) { while (source.checks.get() < 2) delay(10) }
            await(repository, UpdateStatus.Available)
            assertEquals(2, source.checks.get())
        }
    }

    @Test fun failedAutomaticCheckRetriesAfterOneHourAndNeverClaimsLatest(): Unit = runBlocking {
        val source = FakeSource().apply { failure = IOException("offline") }
        withRepository(source) { repository, preferences ->
            repository.onForeground(); await(repository, UpdateStatus.Error)
            assertEquals(UpdateError.Network, repository.state.value.error)
            assertNull(repository.state.value.lastCheckedMillis)
            assertEquals(clock, preferences.preferences.first().lastAttemptMillis)
            clock += 59 * 60 * 1000L
            repository.onForeground(); delay(100); assertEquals(1, source.checks.get())
            source.failure = null; clock += 60 * 1000L
            repository.onForeground(); await(repository, UpdateStatus.Available)
            assertEquals(2, source.checks.get())
        }
    }

    @Test fun simultaneousForegroundAndManualRequestsPerformOnlyOneNetworkCheck(): Unit = runBlocking {
        val entered = CountDownLatch(1)
        val releaseCheck = CountDownLatch(1)
        val source = FakeSource().apply { onCheck = { entered.countDown(); check(releaseCheck.await(5, TimeUnit.SECONDS)) } }
        try {
            withRepository(source) { repository, _ ->
                repository.onForeground()
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                repeat(30) { repository.check(manual = true); repository.onForeground() }
                assertEquals(1, source.checks.get())
                releaseCheck.countDown()
                await(repository, UpdateStatus.Available)
                assertEquals(1, source.checks.get())
            }
        } finally { releaseCheck.countDown() }
    }

    @Test fun noPublishedReleaseAndInstalledVersionAreReportedSeparately(): Unit = runBlocking {
        val source = FakeSource().apply { latestRelease = null }
        withRepository(source) { repository, _ ->
            repository.check(true); await(repository, UpdateStatus.NoRelease)
            assertNull(repository.state.value.release)
            source.latestRelease = release.copy(versionName = "0.1.0", version = ReleaseVersion(0, 1, 0))
            repository.check(true); await(repository, UpdateStatus.Latest)
            assertNull(repository.state.value.release)
        }
    }

    @Test fun completedPrivateDownloadAndDismissalAreRestoredWithoutAnotherNetworkCall(): Unit = runBlocking {
        val source = FakeSource()
        withRepository(source) { repository, _ ->
            repository.check(true); await(repository, UpdateStatus.Available)
            assertTrue(repository.state.value.prompt)
            repository.dismissPrompt(); delay(100)
            repository.download(); await(repository, UpdateStatus.Ready)
            assertArrayEquals(payload, repository.validatedApk()!!.readBytes())
            repository.reportInstallError(UpdateError.PermissionRequired)
            assertEquals(UpdateStatus.Ready, repository.state.value.status)
            assertNotNull(repository.validatedApk())
            assertNull(repository.state.value.error)
        }
        temporary.root.resolve("updates/release-${"0".repeat(64)}.apk").writeBytes(payload)
        temporary.root.resolve("updates/interrupted.part").writeBytes(payload)
        withRepository(source) { repository, _ ->
            await(repository, UpdateStatus.Ready)
            assertEquals(1, source.checks.get())
            assertFalse(repository.state.value.prompt)
            assertArrayEquals(payload, repository.validatedApk()!!.readBytes())
            assertEquals(listOf("release-${release.sha256}.apk"), temporary.root.resolve("updates").listFiles().orEmpty().map { it.name })
        }
    }

    @Test fun installedUpdateCacheAndInterruptedDownloadsAreRemovedOnNextProcess(): Unit = runBlocking {
        val source = FakeSource()
        withRepository(source) { repository, _ ->
            repository.check(true); await(repository, UpdateStatus.Available)
            repository.download(); await(repository, UpdateStatus.Ready)
        }
        withRepository(source, installedVersion = release.version) { repository, _ ->
            repository.check(true); await(repository, UpdateStatus.Latest)
            assertTrue(temporary.root.resolve("updates").listFiles().orEmpty().isEmpty())
            assertNull(repository.validatedApk())
        }
    }

    @Test fun dismissingThenImmediatelyRecheckingTheSameVersionDoesNotReopenPrompt(): Unit = runBlocking {
        withRepository(FakeSource()) { repository, _ ->
            repository.check(true); await(repository, UpdateStatus.Available)
            repository.dismissPrompt()
            repository.check(true); await(repository, UpdateStatus.Available)
            assertFalse(repository.state.value.prompt)
        }
    }

    @Test fun frameworkRuntimeFailureIsReportedInsteadOfEscapingTheApplicationScope(): Unit = runBlocking {
        val source = FakeSource().apply { failure = SecurityException("network denied") }
        withRepository(source) { repository, _ ->
            repository.check(true); await(repository, UpdateStatus.Error)
            assertEquals(UpdateError.Network, repository.state.value.error)
        }
    }

    @Test fun lateReturnOfAnEarlierToggleCannotOverwriteTheNewerPersistedAndDisplayedSetting(): Unit = runBlocking {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        val delegate = PreferenceDataStoreFactory.create(scope = scope) { temporary.root.resolve("updates.preferences_pb") }
        val store = PausedToggleReturnStore(delegate)
        val preferences = UpdatePreferencesRepository(store)
        val repository = AppUpdateRepository(preferences, FakeSource(), temporary.root.resolve("updates"),
            ReleaseVersion(0, 1, 0), ::verifyUpdatePayload, scope) { clock }
        try {
            repository.setAutoCheck(false)
            withTimeout(5000) { store.firstCommitted.await(); repository.state.first { !it.autoCheck } }
            repository.setAutoCheck(true)
            withTimeout(5000) { repository.state.first { it.autoCheck } }
            await(repository, UpdateStatus.Available)
            assertTrue(preferences.preferences.first().autoCheck)
            // The first write already committed; only its caller's return is delayed until the newer
            // value has been persisted, collected, and the automatic check has completely finished.
            store.resumeFirstReturn.complete(Unit)
            withTimeout(5000) { store.heldCaller!!.join() }
            assertTrue("An old caller must not overwrite the collector's newer UI value", repository.state.value.autoCheck)
            assertTrue(preferences.preferences.first().autoCheck)
        } finally {
            store.resumeFirstReturn.complete(Unit)
            job.cancelAndJoin()
        }
    }

    @Test fun changedCachedApkFailsRevalidationBeforeInstallAndCannotRemainReady(): Unit = runBlocking {
        withRepository(FakeSource()) { repository, _ ->
            repository.check(true); await(repository, UpdateStatus.Available)
            repository.download(); await(repository, UpdateStatus.Ready)
            repository.validatedApk()!!.writeBytes(ByteArray(payload.size))
            assertNull(repository.validatedApk())
            assertEquals(UpdateStatus.Error, repository.state.value.status)
            assertEquals(UpdateError.InvalidApk, repository.state.value.error)
        }
    }

    @Test fun signatureFailureHasActionableErrorAndDoesNotPublishInstallerFile(): Unit = runBlocking {
        withRepository(FakeSource(), validator = { _, _, _ -> throw ApkValidationException(ApkValidationFailure.Signature) }) { repository, _ ->
            repository.check(true); await(repository, UpdateStatus.Available)
            repository.download(); await(repository, UpdateStatus.Error)
            assertEquals(UpdateError.IncompatibleSignature, repository.state.value.error)
            assertNull(repository.validatedApk())
            assertTrue(temporary.root.resolve("updates").listFiles().orEmpty().none { it.extension == "apk" })
        }
    }

    @Test fun cancelledSourceCannotPublishStaleBytesEvenIfItReturnsNormally(): Unit = runBlocking {
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val source = FakeSource().apply { onDownload = { entered.countDown(); check(resume.await(5, TimeUnit.SECONDS)); returned.countDown() } }
        try {
            withRepository(source) { repository, _ ->
                repository.check(true); await(repository, UpdateStatus.Available)
                repository.download(); assertTrue(entered.await(5, TimeUnit.SECONDS))
                repository.cancelDownload()
                assertEquals(UpdateStatus.Available, repository.state.value.status)
                resume.countDown(); assertTrue(returned.await(5, TimeUnit.SECONDS))
                withTimeout(3000) { while (temporary.root.resolve("updates").listFiles().orEmpty().any { it.name.endsWith(".part") }) delay(10) }
                assertEquals(1, source.cancels.get())
                assertNull(repository.validatedApk())
                assertTrue(temporary.root.resolve("updates").listFiles().orEmpty().none { it.extension == "apk" })
            }
        } finally { resume.countDown() }
    }

    @Test fun cancellingDuringStoragePreparationCannotRepublishDownloadingOrStartTheSource(): Unit = runBlocking {
        val spaceEntered = CountDownLatch(1)
        val resumeStorage = CountDownLatch(1)
        val directory = object : File(temporary.root, "updates") {
            override fun getUsableSpace(): Long {
                spaceEntered.countDown()
                check(resumeStorage.await(5, TimeUnit.SECONDS))
                return super.getUsableSpace()
            }
        }
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        val preferences = UpdatePreferencesRepository(PreferenceDataStoreFactory.create(scope = scope) {
            temporary.root.resolve("updates.preferences_pb")
        })
        val source = FakeSource()
        val repository = AppUpdateRepository(preferences, source, directory, ReleaseVersion(0, 1, 0),
            ::verifyUpdatePayload, scope) { clock }
        try {
            repository.check(true); await(repository, UpdateStatus.Available)
            val existingJobs = job.children.toSet()
            repository.download()
            assertTrue(spaceEntered.await(5, TimeUnit.SECONDS))
            val downloadJob = job.children.single { it !in existingJobs }
            assertEquals(UpdateStatus.Available, repository.state.value.status)
            repository.cancelDownload()
            resumeStorage.countDown()
            withTimeout(5000) { downloadJob.join() }
            assertEquals(UpdateStatus.Available, repository.state.value.status)
            assertEquals(0, source.downloads.get())
            assertNull(repository.validatedApk())
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            resumeStorage.countDown()
            job.cancelAndJoin()
        }
    }

    private suspend fun withRepository(source: FakeSource,
        validator: (File, UpdateRelease, () -> Unit) -> Unit = ::verifyUpdatePayload,
        installedVersion: ReleaseVersion = ReleaseVersion(0, 1, 0),
        block: suspend (AppUpdateRepository, UpdatePreferencesRepository) -> Unit) {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        val preferences = UpdatePreferencesRepository(PreferenceDataStoreFactory.create(scope = scope) {
            temporary.root.resolve("updates.preferences_pb")
        })
        val repository = AppUpdateRepository(preferences, source, temporary.root.resolve("updates"), installedVersion, validator, scope) { clock }
        try { block(repository, preferences) } finally { job.cancelAndJoin() }
    }

    private suspend fun await(repository: AppUpdateRepository, status: UpdateStatus) {
        withTimeout(5000) { repository.state.first { it.status == status } }
        delay(60) // Let the operation finish after its final state emission before issuing another command.
    }

    private inner class FakeSource : UpdateSource {
        val checks = AtomicInteger()
        val downloads = AtomicInteger()
        val cancels = AtomicInteger()
        @Volatile var failure: Exception? = null
        @Volatile var latestRelease: UpdateRelease? = release
        var onCheck: () -> Unit = {}
        var onDownload: () -> Unit = {}
        override fun latest(): UpdateRelease? { checks.incrementAndGet(); onCheck(); failure?.let { throw it }; return latestRelease }
        override fun download(release: UpdateRelease, destination: File, onProgress: (Long, Long) -> Unit, checkCancelled: () -> Unit) {
            downloads.incrementAndGet(); onDownload(); destination.writeBytes(payload); onProgress(payload.size.toLong(), payload.size.toLong())
        }
        override fun cancel() { cancels.incrementAndGet() }
    }

    private class PausedToggleReturnStore(private val delegate: DataStore<Preferences>) : DataStore<Preferences> {
        override val data: Flow<Preferences> = delegate.data
        private val first = AtomicBoolean(true)
        val firstCommitted = CompletableDeferred<Unit>()
        val resumeFirstReturn = CompletableDeferred<Unit>()
        @Volatile var heldCaller: Job? = null
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val value = delegate.updateData(transform)
            if (value[booleanPreferencesKey("auto_check")] == false && first.compareAndSet(true, false)) {
                heldCaller = currentCoroutineContext()[Job]
                firstCommitted.complete(Unit)
                resumeFirstReturn.await()
            }
            return value
        }
    }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
}
