package com.riftdeck.data.repository

import android.content.Context
import com.riftdeck.BuildConfig
import com.riftdeck.core.update.ApkValidationException
import com.riftdeck.core.update.ApkValidationFailure
import com.riftdeck.core.update.ApkValidator
import com.riftdeck.core.update.GitHubUpdateSource
import com.riftdeck.core.update.ReleaseVersion
import com.riftdeck.core.update.UpdateRelease
import com.riftdeck.core.update.UpdateSource
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class UpdateStatus { Idle, Checking, Available, Latest, NoRelease, Downloading, Ready, Error }
enum class UpdateError { Network, Storage, InvalidApk, IncompatibleSignature, PermissionRequired, InstallerUnavailable }
data class UpdateUiState(
    val status: UpdateStatus = UpdateStatus.Idle,
    val release: UpdateRelease? = null,
    val progressBytes: Long = 0,
    val totalBytes: Long = 0,
    val autoCheck: Boolean = true,
    val lastCheckedMillis: Long? = null,
    val prompt: Boolean = false,
    val error: UpdateError? = null,
)

/** Foreground checks are opt-out and throttled. Construction restores private cache without networking. */
class AppUpdateRepository internal constructor(
    private val preferences: UpdatePreferencesRepository,
    private val source: UpdateSource,
    private val directory: File,
    private val installedVersion: ReleaseVersion,
    private val validator: (File, UpdateRelease, () -> Unit) -> Unit,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutableState = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = mutableState.asStateFlow()
    private val initialized = CompletableDeferred<Unit>()
    private val operation = Mutex()
    private val jobLock = Any()
    private var workJob: Job? = null
    private var downloading = false
    private val generation = AtomicLong()
    private val dismissedInProcess = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    @Volatile private var saved = UpdatePreferences()
    @Volatile private var readyFile: File? = null

    init {
        scope.launch(Dispatchers.IO) {
            try {
                saved = preferences.preferences.first()
                ensureDirectory()
                val release = saved.release?.takeIf { it.version > installedVersion }
                // Preserve the current downloadable update; older versions and interrupted staging are bounded.
                cleanPrivateCache(release)
                val coroutine = currentCoroutineContext()
                if (release != null) restoreRelease(release) { coroutine.ensureActive() }
                mutableState.update { it.copy(autoCheck = saved.autoCheck,
                    lastCheckedMillis = saved.lastCheckedMillis.takeIf { time -> time > 0 }) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutableState.update { it.copy(status = UpdateStatus.Error, error = UpdateError.Storage) }
            } finally { initialized.complete(Unit) }
            preferences.preferences.catch { error ->
                if (error is CancellationException) throw error
                publishError(UpdateError.Storage)
            }.collect { value ->
                saved = value
                mutableState.update { it.copy(autoCheck = value.autoCheck,
                    lastCheckedMillis = value.lastCheckedMillis.takeIf { time -> time > 0 }) }
            }
        }
    }

    fun onForeground() = check(manual = false)

    fun check(manual: Boolean = false) = startWork(isDownload = false) {
        try { saved = preferences.preferences.first() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { publishError(UpdateError.Storage); return@startWork }
        if (!manual && (!saved.autoCheck || !shouldAutoCheck(saved, now()))) return@startWork
        val attempted = now()
        try {
            preferences.markAttempt(attempted)
            saved = saved.copy(lastAttemptMillis = attempted)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { publishError(UpdateError.Storage); return@startWork }
        mutableState.update { it.copy(status = UpdateStatus.Checking, error = null) }
        val release = try { source.latest() } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { publishError(UpdateError.Network); return@startWork }
        currentCoroutineContext().ensureActive()
        val available = release?.takeIf { it.version > installedVersion }
        try {
            val checked = now()
            preferences.recordSuccess(checked, available)
            saved = saved.copy(lastCheckedMillis = checked, release = available)
            val coroutine = currentCoroutineContext()
            if (available != null) restoreRelease(available) { coroutine.ensureActive() }
            else {
                readyFile = null
                mutableState.update { it.copy(status = if (release == null) UpdateStatus.NoRelease else UpdateStatus.Latest,
                    release = null, progressBytes = 0, totalBytes = 0, prompt = false, error = null) }
            }
            mutableState.update { it.copy(lastCheckedMillis = checked) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { publishError(UpdateError.Storage) }
    }

    fun setAutoCheck(enabled: Boolean) {
        scope.launch(Dispatchers.IO) {
            initialized.await()
            try {
                preferences.setAutoCheck(enabled)
                if (enabled) onForeground()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { publishError(UpdateError.Storage) }
        }
    }

    fun dismissPrompt() {
        val version = state.value.release?.versionName ?: return
        dismissedInProcess.add(version)
        mutableState.update { it.copy(prompt = false) }
        scope.launch(Dispatchers.IO) {
            initialized.await()
            try {
                preferences.dismissVersion(version)
                saved = saved.copy(dismissedVersion = version)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { publishError(UpdateError.Storage) }
        }
    }

    fun download() = startWork(isDownload = true) { token ->
        val release = state.value.release ?: return@startWork
        var staging: File? = null
        val coroutine = currentCoroutineContext()
        val guard = {
            coroutine.ensureActive()
            if (generation.get() != token) throw CancellationException("Update download cancelled")
        }
        fun fail(error: UpdateError, invalidate: Boolean = false) {
            synchronized(jobLock) {
                guard()
                if (invalidate) readyFile = null
                publishError(error)
            }
        }
        try {
            ensureDirectory()
            if (directory.usableSpace < release.apkSize + 8L * 1024 * 1024) {
                fail(UpdateError.Storage); return@startWork
            }
            val incoming = File.createTempFile("download-", ".part", directory)
            staging = incoming
            synchronized(jobLock) {
                guard()
                mutableState.update { it.copy(status = UpdateStatus.Downloading, progressBytes = 0,
                    totalBytes = release.apkSize, prompt = false, error = null) }
            }
            try {
                source.download(release, incoming, { bytes, total ->
                    synchronized(jobLock) {
                        guard()
                        mutableState.update { it.copy(progressBytes = bytes.coerceIn(0, release.apkSize),
                            totalBytes = total.takeIf { count -> count == release.apkSize } ?: release.apkSize) }
                    }
                }, guard)
            } catch (invalid: ApkValidationException) { throw invalid }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { fail(UpdateError.Network); return@startWork }
            guard()
            validator(incoming, release, guard)
            val destination = apkFile(release)
            synchronized(jobLock) {
                guard()
                try {
                    Files.move(incoming.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(incoming.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                staging = null
                readyFile = destination
                mutableState.update { it.copy(status = UpdateStatus.Ready, progressBytes = release.apkSize,
                    totalBytes = release.apkSize, error = null) }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (invalid: ApkValidationException) { fail(validationError(invalid), invalidate = true) }
        catch (_: Exception) { fail(UpdateError.Storage) }
        finally { runCatching { staging?.delete() } }
    }

    fun cancelDownload() {
        synchronized(jobLock) {
            if (!downloading || workJob?.isActive != true) return
            generation.incrementAndGet()
            workJob?.cancel()
            runCatching { source.cancel() }
            mutableState.update { it.copy(status = if (readyFile != null) UpdateStatus.Ready else UpdateStatus.Available,
                progressBytes = 0, error = null) }
        }
    }

    /** The file is private, and is checked again on every install attempt, including permission return. */
    suspend fun validatedApk(): File? = withContext(Dispatchers.IO) {
        initialized.await()
        operation.withLock {
            val release = state.value.release ?: return@withLock null
            val file = readyFile ?: return@withLock null
            try {
                val coroutine = currentCoroutineContext()
                validator(file, release) { coroutine.ensureActive() }
                mutableState.update { it.copy(status = UpdateStatus.Ready, error = null) }
                file
            } catch (invalid: ApkValidationException) {
                readyFile = null
                publishError(validationError(invalid))
                null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                readyFile = null
                publishError(UpdateError.Storage)
                null
            }
        }
    }

    fun reportInstallError(error: UpdateError) {
        mutableState.update { it.copy(status = if (readyFile != null) UpdateStatus.Ready else UpdateStatus.Error, error = error) }
    }

    private fun startWork(isDownload: Boolean, block: suspend (Long) -> Unit) {
        synchronized(jobLock) {
            if (workJob?.isActive == true) return
            val token = generation.incrementAndGet()
            downloading = isDownload
            workJob = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
                initialized.await()
                operation.withLock {
                    try { block(token) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { publishError(UpdateError.Storage) }
                }
            }.also { it.start() }
        }
    }

    private fun restoreRelease(release: UpdateRelease, guard: () -> Unit = {}) {
        readyFile = null
        val candidate = apkFile(release)
        var error: UpdateError? = null
        if (candidate.isFile) {
            try { validator(candidate, release, guard); readyFile = candidate }
            catch (invalid: ApkValidationException) { error = validationError(invalid) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = UpdateError.Storage }
        }
        mutableState.update { it.copy(status = if (readyFile != null) UpdateStatus.Ready else UpdateStatus.Available,
            release = release, progressBytes = if (readyFile != null) release.apkSize else 0,
            totalBytes = release.apkSize, prompt = !isDismissed(release), error = error) }
    }

    private fun cleanPrivateCache(release: UpdateRelease?) {
        val retained = release?.let(::apkFile)?.name
        directory.listFiles()?.filter { file ->
            file.isFile && (file.name.endsWith(".part") ||
                (file.name.matches(Regex("release-[0-9a-f]{64}\\.apk")) && file.name != retained))
        }?.forEach { if (!it.delete()) throw IOException("Obsolete update cache unavailable") }
    }

    private fun isDismissed(release: UpdateRelease) = release.versionName in dismissedInProcess ||
        saved.dismissedVersion == release.versionName

    private fun ensureDirectory() {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Update storage unavailable")
    }
    private fun apkFile(release: UpdateRelease): File {
        if (!release.sha256.matches(Regex("[0-9a-f]{64}"))) throw ApkValidationException(ApkValidationFailure.Payload)
        return File(directory, "release-${release.sha256}.apk")
    }
    private fun publishError(error: UpdateError) {
        mutableState.update { it.copy(status = if (readyFile != null) UpdateStatus.Ready else UpdateStatus.Error, error = error) }
    }

    companion object {
        fun create(context: Context, applicationScope: CoroutineScope): AppUpdateRepository {
            val app = context.applicationContext
            val validator = ApkValidator(app)
            return AppUpdateRepository(UpdatePreferencesRepository(app), GitHubUpdateSource(),
                File(app.filesDir, "updates"), ReleaseVersion.parse(BuildConfig.VERSION_NAME)!!,
                validator::validate, applicationScope)
        }
    }
}

internal fun shouldAutoCheck(preferences: UpdatePreferences, now: Long): Boolean {
    if (preferences.lastAttemptMillis <= 0 || now < preferences.lastAttemptMillis) return true
    val delay = if (preferences.lastAttemptMillis > preferences.lastCheckedMillis) 60L * 60 * 1000 else 24L * 60 * 60 * 1000
    return now - preferences.lastAttemptMillis >= delay
}

private fun validationError(error: ApkValidationException): UpdateError =
    if (error.failure == ApkValidationFailure.Signature) UpdateError.IncompatibleSignature else UpdateError.InvalidApk
