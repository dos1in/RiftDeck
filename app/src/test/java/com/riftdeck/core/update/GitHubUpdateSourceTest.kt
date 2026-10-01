package com.riftdeck.core.update

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GitHubUpdateSourceTest {
    @get:Rule val folder = TemporaryFolder()
    private val payload = ByteArray(200_123) { (it * 17).toByte() }

    @Test fun stableVersionsCompareNumericallyAndRejectUnsupportedTags() {
        assertEquals(ReleaseVersion(1, 2, 3), ReleaseVersion.parse("v1.2.3"))
        assertEquals(ReleaseVersion(1, 2, 3), ReleaseVersion.parse("V1.2.3"))
        assertEquals("0.1.0", ReleaseVersion.parse("0.1.0").toString())
        assertTrue(ReleaseVersion(1, 2, 10) > ReleaseVersion(1, 2, 9))
        assertTrue(ReleaseVersion(1, 10, 0) > ReleaseVersion(1, 9, 99))
        assertTrue(ReleaseVersion(Int.MAX_VALUE, 0, 0) > ReleaseVersion(0, Int.MAX_VALUE, Int.MAX_VALUE))
        listOf("1.2", "1", "1.2.3.4", "01.2.3", "1.02.3", "1.2.03", "-1.2.3", "1.2.3-beta", "v1.2.3+build", " 1.2.3", "1.2.3\n", "2147483648.1.0", "999999999999999.0.0").forEach {
            assertNull("Unexpected stable version $it", ReleaseVersion.parse(it))
        }
    }

    @Test fun strictJsonRejectsCommentsCoercionsDuplicateKeysAndMalformedUnicode() {
        validateStrictReleaseJson("{\"body\":\"中文 🎮\\nnotes\\uD83D\\uDE00\",\"assets\":[1,-2,0.5,1e3,true,false,null]}")
        listOf("{draft:false}", "{'draft':false}", "{\"draft\":false,}", "{\"assets\":[1,]}", "{/*comment*/\"draft\":false}",
            "{\"draft\":false,\"draft\":true}", "{\"draft\":false,\"\\u0064raft\":true}", "{\"body\":\"\\uD800\"}", "{\"body\":\"\\uDC00\"}",
            "{\"size\":01}", "{\"size\":.5}", "{\"size\":NaN}", "{\"size\":1e}", "{\"body\":\"raw\nnewline\"}", "{} trailing").forEach {
            assertThrows(IOException::class.java) { validateStrictReleaseJson(it) }
        }
        assertThrows(IOException::class.java) { validateStrictReleaseJson("[".repeat(34) + "0" + "]".repeat(34)) }
    }

    @Test fun canonicalUniversalApkWinsIndependentOfAssetOrder() {
        val canonical = asset("RiftDeck-v1.2.3.apk")
        val alternatives = listOf(asset("RiftDeck-arm64.apk"), asset("RiftDeck-debug.apk"), asset("app-release-unsigned.apk"), asset("other.apk"), canonical)
        assertEquals(canonical.url, selectGitHubRelease(metadata(alternatives))!!.apkUrl)
        assertEquals(canonical.url, selectGitHubRelease(metadata(alternatives.reversed()))!!.apkUrl)
        assertEquals("1.2.3", selectGitHubRelease(metadata(alternatives))!!.versionName)
    }

    @Test fun incompletePrereleaseAndUnrelatedAssetsAreExcluded() {
        assertNull(selectGitHubRelease(metadata(listOf(asset("RiftDeck-debug.apk"), asset("app-release-unsigned.apk"), asset("RiftDeck-x86_64.apk")))))
        assertNull(selectGitHubRelease(metadata(listOf(asset("RiftDeck.apk").copy(state = "new")))))
        assertNull(selectGitHubRelease(metadata(listOf(asset("RiftDeck.apk"))).copy(prerelease = true)))
        assertNull(selectGitHubRelease(metadata(listOf(asset("RiftDeck.apk"))).copy(draft = true)))
        assertNull(selectGitHubRelease(metadata(listOf(asset("RiftDeck.apk"))).copy(tag = "v1.2.3-rc1")))
        assertNull(selectGitHubRelease(metadata(emptyList())))
    }

    @Test fun ambiguousApksAndMissingDigestCannotBecomeAnAvailableUpdate() {
        assertThrows(IOException::class.java) { selectGitHubRelease(metadata(listOf(asset("one.apk"), asset("two.apk")))) }
        assertThrows(IOException::class.java) { selectGitHubRelease(metadata(listOf(asset("RiftDeck.apk"), asset("riftdeck.apk")))) }
        assertThrows(IOException::class.java) { selectGitHubRelease(metadata(listOf(asset("RiftDeck.apk").copy(digest = null)))) }
        assertThrows(IOException::class.java) { selectGitHubRelease(metadata(listOf(asset("RiftDeck.apk").copy(digest = "md5:abc")))) }
        assertThrows(IOException::class.java) { selectGitHubRelease(metadata(listOf(asset("RiftDeck.apk").copy(size = MAX_UPDATE_APK_BYTES + 1)))) }
        assertThrows(IOException::class.java) { selectGitHubRelease(metadata(listOf(asset("RiftDeck.apk").copy(size = 0)))) }
    }

    @Test fun allInitialUrlsStayInsideTheExpectedHttpsRepository() {
        validateReleasePage("https://github.com/dos1in/RiftDeck/releases/tag/v1.2.3", "v1.2.3")
        validateMetadataUrl(LATEST_RELEASE_URL)
        listOf("http://github.com/dos1in/RiftDeck/releases/tag/v1.2.3", "https://github.com/other/RiftDeck/releases/tag/v1.2.3", "https://github.com/dos1in/Other/releases/tag/v1.2.3", "https://github.com@attacker.test/dos1in/RiftDeck/releases/tag/v1.2.3", "https://github.com:8443/dos1in/RiftDeck/releases/tag/v1.2.3", "https://github.com/dos1in/RiftDeck/releases/tag/v1.2.3?url=evil", "https://github.com/dos1in/RiftDeck/releases/tag/v1.2.3#evil").forEach {
            assertThrows(IOException::class.java) { validateReleasePage(it, "v1.2.3") }
        }
        assertThrows(IOException::class.java) { validateApkUrl("https://github.com/dos1in/RiftDeck/releases/download/v1.2.3/other.apk", "v1.2.3", "RiftDeck.apk") }
        assertThrows(IOException::class.java) { validateApkUrl("https://github.com/dos1in/RiftDeck/releases/download/v1.2.3/%2e%2e/RiftDeck.apk", "v1.2.3", "RiftDeck.apk") }
        assertThrows(IOException::class.java) { validateMetadataUrl("https://api.github.com/repos/other/RiftDeck/releases/latest") }
    }

    @Test fun redirectsAllowOnlyKnownGitHubAssetHostsAndHttps() {
        val release = release()
        validateAssetRedirect("https://release-assets.githubusercontent.com/github-production-release-asset/test?signature=abc", release)
        validateAssetRedirect("https://objects.githubusercontent.com/github-production-release-asset/test", release)
        listOf("http://release-assets.githubusercontent.com/test", "https://release-assets.githubusercontent.com.attacker.test/test", "https://attacker.test/test", "https://raw.githubusercontent.com/other/repo/file.apk", "https://github.com/other/repo/releases/download/v1.2.3/RiftDeck.apk").forEach {
            assertThrows(IOException::class.java) { validateAssetRedirect(it, release) }
        }
    }

    @Test fun noReleaseAndRateLimitsDoNotPretendToFindUpdates() {
        val notFound = FakeConnection(URL(LATEST_RELEASE_URL), 404)
        assertNull(GitHubUpdateSource { notFound }.latest())
        assertTrue(notFound.disconnected)
        assertEquals(10_000, notFound.connectTimeout)
        assertEquals(15_000, notFound.readTimeout)
        val limited = FakeConnection(URL(LATEST_RELEASE_URL), 403)
        assertThrows(IOException::class.java) { GitHubUpdateSource { limited }.latest() }
        assertTrue(limited.disconnected)
    }

    @Test fun metadataLimitAppliesWithAndWithoutContentLength() {
        val declared = FakeConnection(URL(LATEST_RELEASE_URL), 200, declaredLength = MAX_UPDATE_METADATA_BYTES.toLong() + 1)
        assertThrows(IOException::class.java) { GitHubUpdateSource { declared }.latest() }
        assertFalse(declared.inputOpened)
        val chunked = FakeConnection(URL(LATEST_RELEASE_URL), 200, body = ByteArray(MAX_UPDATE_METADATA_BYTES + 1) { 65 })
        assertThrows(IOException::class.java) { GitHubUpdateSource { chunked }.latest() }
        assertTrue(chunked.disconnected)
    }

    @Test fun apkStreamsThroughRedirectAndPublishesOnlyTheVerifiedContent() {
        val release = release()
        val redirect = FakeConnection(URL(release.apkUrl), 302, location = "https://release-assets.githubusercontent.com/test?signature=abc")
        val response = FakeConnection(URL(redirect.location!!), 200, body = payload, declaredLength = payload.size.toLong())
        val requests = mutableListOf<URL>()
        val source = GitHubUpdateSource { url -> requests.add(url); if (url.host == "github.com") redirect else response }
        val target = folder.root.resolve("RiftDeck.apk").also { it.writeText("previous verified APK") }
        val progress = mutableListOf<Long>()
        source.download(release, target, { current, total -> assertEquals(payload.size.toLong(), total); progress.add(current) }, {})
        assertArrayEquals(payload, target.readBytes())
        assertEquals(listOf(URL(release.apkUrl), URL(redirect.location)), requests)
        assertTrue(progress.zipWithNext().all { (first, second) -> second >= first && second - first <= 64 * 1024 })
        assertEquals(0L, progress.first())
        assertEquals(payload.size.toLong(), progress.last())
        assertTrue(redirect.disconnected && response.disconnected)
        assertNoStaging()
    }

    @Test fun changedLengthOrChecksumLeavesAnExistingCompleteApkUntouched() {
        val release = release()
        val target = folder.root.resolve("RiftDeck.apk").also { it.writeText("previous verified APK") }
        val bodies = listOf(payload.copyOf(payload.size - 1), payload + 1.toByte(), payload.copyOf().apply { this[0] = 99 })
        for (body in bodies) {
            val source = GitHubUpdateSource { FakeConnection(it, 200, body = body) }
            assertThrows(IOException::class.java) { source.download(release, target, { _, _ -> }, {}) }
            assertEquals("previous verified APK", target.readText())
            assertNoStaging()
        }
        val mismatch = GitHubUpdateSource { FakeConnection(it, 200, body = payload, declaredLength = payload.size.toLong() + 1) }
        assertThrows(IOException::class.java) { mismatch.download(release, target, { _, _ -> }, {}) }
        assertEquals("previous verified APK", target.readText())
    }

    @Test fun cancellationClosesTheConnectionAndRemovesPartialDownload() {
        val connection = FakeConnection(URL(release().apkUrl), 200, body = payload)
        val source = GitHubUpdateSource { connection }
        val target = folder.root.resolve("RiftDeck.apk")
        assertThrows(InterruptedIOException::class.java) {
            source.download(release(), target, { current, _ -> if (current > 0) source.cancel() }, {})
        }
        assertTrue(connection.disconnected)
        assertFalse(target.exists())
        assertNoStaging()
    }

    @Test(timeout = 10_000)
    fun cancellingAPendingNetworkReadDisconnectsImmediately() {
        val reading = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val connection = object : FakeConnection(URL(release().apkUrl), 200) {
            override fun getInputStream(): InputStream = object : InputStream() {
                override fun read(): Int { reading.countDown(); closed.await(5, TimeUnit.SECONDS); throw IOException("Diagnostic disconnection") }
            }
            override fun disconnect() { super.disconnect(); closed.countDown() }
        }
        val source = GitHubUpdateSource { connection }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val download = executor.submit<Boolean> {
                try { source.download(release(), folder.root.resolve("RiftDeck.apk"), { _, _ -> }, {}); false }
                catch (_: IOException) { true }
            }
            assertTrue(reading.await(2, TimeUnit.SECONDS))
            source.cancel()
            assertTrue(download.get(2, TimeUnit.SECONDS))
            assertNoStaging()
        } finally { closed.countDown(); executor.shutdownNow() }
    }

    @Test fun untrustedRedirectIsRejectedBeforeOpeningAnotherConnection() {
        val first = FakeConnection(URL(release().apkUrl), 302, location = "https://attacker.test/update.apk")
        var requests = 0
        val source = GitHubUpdateSource { requests++; first }
        assertThrows(IOException::class.java) { source.download(release(), folder.root.resolve("RiftDeck.apk"), { _, _ -> }, {}) }
        assertEquals(1, requests)
        assertTrue(first.disconnected)
        assertNoStaging()
    }

    private fun release() = selectGitHubRelease(metadata(listOf(asset("RiftDeck-v1.2.3.apk"))))!!
    private fun asset(name: String) = ReleaseAsset(name, "https://github.com/dos1in/RiftDeck/releases/download/v1.2.3/$name", payload.size.toLong(), "sha256:${hash(payload)}")
    private fun metadata(assets: List<ReleaseAsset>) = ReleaseMetadata("v1.2.3", false, false, "Original diagnostic release notes", "https://github.com/dos1in/RiftDeck/releases/tag/v1.2.3", assets)
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun assertNoStaging() = assertTrue(folder.root.listFiles().orEmpty().none { it.name.endsWith(".part") })

    private open class FakeConnection(url: URL, private val status: Int, private val body: ByteArray = ByteArray(0), val location: String? = null, private val declaredLength: Long = -1) : HttpURLConnection(url) {
        var disconnected = false
        var inputOpened = false
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode(): Int = status
        override fun getContentLengthLong(): Long = declaredLength
        override fun getHeaderField(name: String?): String? = if (name == "Location") location else null
        override fun getInputStream(): InputStream { inputOpened = true; return ByteArrayInputStream(body) }
    }
}
