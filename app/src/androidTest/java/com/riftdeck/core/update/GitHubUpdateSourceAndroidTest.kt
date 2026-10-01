package com.riftdeck.core.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Exercise Android's actual JSON parser and file APIs without contacting a release server. */
class GitHubUpdateSourceAndroidTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val payload = "Original diagnostic APK transfer bytes".toByteArray()
    private lateinit var directory: File

    @Before fun prepare() { directory = File(context.cacheDir, "update-source-tests-${UUID.randomUUID()}").also { it.mkdirs() } }
    @After fun cleanup() { directory.deleteRecursively() }

    @Test fun actualAndroidJsonCreatesTypedStableReleaseWithCanonicalApk() {
        val root = metadata()
        root.put("body", "更新说明 🎮\n第二行")
        val assets = root.getJSONArray("assets")
        assets.put(asset("RiftDeck-arm64.apk"))
        assets.put(asset("app-release-unsigned.apk"))
        assets.put(asset("another.apk"))
        val release = source(root.toString()).latest()!!
        assertEquals("v1.2.3", release.tag)
        assertEquals("1.2.3", release.versionName)
        assertEquals(ReleaseVersion(1, 2, 3), release.version)
        assertEquals("更新说明 🎮\n第二行", release.notes)
        assertEquals(payload.size.toLong(), release.apkSize)
        assertEquals(digest(payload), release.sha256)
        assertTrue(release.apkUrl.endsWith("RiftDeck-v1.2.3.apk"))
    }

    @Test fun nullNotesAndNoCompatibleAssetsAreHandledWithoutTypeCoercion() {
        val root = metadata().put("body", JSONObject.NULL)
        assertEquals("", source(root.toString()).latest()!!.notes)
        root.put("assets", JSONArray().put(asset("diagnostic.zip")))
        assertNull(source(root.toString()).latest())
        assertNull(source(metadata().put("prerelease", true).toString()).latest())
        assertNull(source(metadata().put("draft", true).toString()).latest())
        assertNull(source(metadata().put("tag_name", "v1.2.3-beta").toString()).latest())
    }

    @Test fun stringsAndDecimalSizesCannotBeCoercedIntoTrustedFields() {
        val corrupt = listOf(
            metadata().put("draft", "false"),
            metadata().put("prerelease", JSONObject.NULL),
            metadata().put("assets", "[]"),
            metadata().also { it.getJSONArray("assets").getJSONObject(0).put("size", payload.size.toString()) },
            metadata().also { it.getJSONArray("assets").getJSONObject(0).put("size", 1.5) },
            metadata().also { it.getJSONArray("assets").getJSONObject(0).put("digest", JSONObject.NULL) },
        )
        corrupt.forEach { assertThrows(IOException::class.java) { source(it.toString()).latest() } }
    }

    @Test fun androidLenientJsonSyntaxIsRejectedBeforeParsingMetadata() {
        val json = metadata().toString()
        listOf(json.replace("\"draft\":false", "draft:false"), json.dropLast(1) + ",}", json.replaceFirst("{", "{/* comment */"),
            json.dropLast(1) + ",\"draft\":false}", json + "{}", "[]").forEach {
            assertThrows(IOException::class.java) { source(it).latest() }
        }
        val invalidUtf8 = FakeConnection(URL(LATEST_RELEASE_URL), 200, body = byteArrayOf(0xff.toByte()))
        assertThrows(IOException::class.java) { GitHubUpdateSource { invalidUtf8 }.latest() }
        assertTrue(invalidUtf8.disconnected)
    }

    @Test fun realParserRejectsReleaseOrDownloadUrlsForAnotherRepository() {
        val page = metadata().put("html_url", "https://github.com/attacker/RiftDeck/releases/tag/v1.2.3")
        assertThrows(IOException::class.java) { source(page.toString()).latest() }
        val apk = metadata().also { it.getJSONArray("assets").getJSONObject(0).put("browser_download_url", "https://github.com/attacker/RiftDeck/releases/download/v1.2.3/RiftDeck-v1.2.3.apk") }
        assertThrows(IOException::class.java) { source(apk.toString()).latest() }
    }

    @Test fun metadataBoundsApplyToDeclaredAndChunkedResponsesOnAndroid() {
        val declared = FakeConnection(URL(LATEST_RELEASE_URL), 200, declaredLength = MAX_UPDATE_METADATA_BYTES + 1L)
        assertThrows(IOException::class.java) { GitHubUpdateSource { declared }.latest() }
        assertFalse(declared.opened)
        val chunked = FakeConnection(URL(LATEST_RELEASE_URL), 200, body = ByteArray(MAX_UPDATE_METADATA_BYTES + 1) { 32 })
        assertThrows(IOException::class.java) { GitHubUpdateSource { chunked }.latest() }
        assertTrue(chunked.disconnected)
        val missing = FakeConnection(URL(LATEST_RELEASE_URL), 404)
        assertNull(GitHubUpdateSource { missing }.latest())
    }

    @Test fun androidDownloadVerifiesHashAndLengthBeforeReplacingCachedApk() {
        val release = source(metadata().toString()).latest()!!
        val target = File(directory, "RiftDeck.apk").also { it.writeText("previous complete APK") }
        for (body in listOf(payload.copyOf(payload.size - 1), payload + 1.toByte(), payload.copyOf().apply { this[0] = 99 })) {
            val response = FakeConnection(URL(release.apkUrl), 200, body = body)
            assertThrows(ApkValidationException::class.java) { GitHubUpdateSource { response }.download(release, target, { _, _ -> }, {}) }
            assertEquals("previous complete APK", target.readText())
            assertTrue(response.disconnected)
            assertNoStaging()
        }
        val valid = FakeConnection(URL(release.apkUrl), 200, body = payload, declaredLength = payload.size.toLong())
        GitHubUpdateSource { valid }.download(release, target, { _, _ -> }, {})
        assertArrayEquals(payload, target.readBytes())
        assertNoStaging()
    }

    @Test fun redirectValidationOccursBeforeOpeningAnUntrustedConnection() {
        val release = source(metadata().toString()).latest()!!
        val response = FakeConnection(URL(release.apkUrl), 302, location = "https://attacker.test/diagnostic.apk")
        var opened = 0
        val updater = GitHubUpdateSource { opened++; response }
        assertThrows(IOException::class.java) { updater.download(release, File(directory, "RiftDeck.apk"), { _, _ -> }, {}) }
        assertEquals(1, opened)
        assertTrue(response.disconnected)
        assertNoStaging()
    }

    @Test(timeout = 10_000)
    fun cancellationDisconnectsAndroidParserNetworkReadAndKeepsNoPartialFile() {
        val reading = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val response = object : FakeConnection(URL(LATEST_RELEASE_URL), 200) {
            override fun getInputStream(): InputStream = object : InputStream() {
                override fun read(): Int { reading.countDown(); closed.await(5, TimeUnit.SECONDS); throw IOException("Diagnostic disconnection") }
            }
            override fun disconnect() { super.disconnect(); closed.countDown() }
        }
        val updater = GitHubUpdateSource { response }
        val worker = Executors.newSingleThreadExecutor()
        try {
            val result = worker.submit<Boolean> { try { updater.latest(); false } catch (_: IOException) { true } }
            assertTrue(reading.await(2, TimeUnit.SECONDS))
            updater.cancel()
            assertTrue(result.get(2, TimeUnit.SECONDS))
            assertTrue(response.disconnected)
            assertNoStaging()
        } finally { closed.countDown(); worker.shutdownNow() }
    }

    private fun source(json: String) = GitHubUpdateSource { FakeConnection(it, 200, body = json.toByteArray()) }
    private fun metadata() = JSONObject().put("tag_name", "v1.2.3").put("draft", false).put("prerelease", false)
        .put("body", "Original diagnostic release notes").put("html_url", "https://github.com/dos1in/RiftDeck/releases/tag/v1.2.3")
        .put("assets", JSONArray().put(asset("RiftDeck-v1.2.3.apk")))
    private fun asset(name: String) = JSONObject().put("name", name).put("state", "uploaded").put("size", payload.size)
        .put("digest", "sha256:${digest(payload)}").put("browser_download_url", "https://github.com/dos1in/RiftDeck/releases/download/v1.2.3/$name")
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    private fun assertNoStaging() { assertTrue(directory.listFiles().orEmpty().none { it.name.endsWith(".part") }) }

    private open class FakeConnection(url: URL, private val status: Int, private val body: ByteArray = ByteArray(0), private val declaredLength: Long = -1, private val location: String? = null) : HttpURLConnection(url) {
        var disconnected = false
        var opened = false
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getContentLengthLong() = declaredLength
        override fun getHeaderField(name: String?): String? = if (name == "Location") location else null
        override fun getInputStream(): InputStream { opened = true; return ByteArrayInputStream(body) }
    }
}
