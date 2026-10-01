package com.riftdeck.core.update

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

/** Blocking IO only. Public GitHub releases require no account, credentials, or extra dependency. */
class GitHubUpdateSource internal constructor(private val connector: (URL) -> HttpURLConnection) : UpdateSource {
    constructor() : this({ it.openConnection() as HttpURLConnection })
    private val connections = ConcurrentHashMap.newKeySet<HttpURLConnection>()
    private val generation = AtomicLong()

    override fun latest(): UpdateRelease? {
        val token = generation.get()
        val check = { checkActive(token) }
        val connection = request(LATEST_RELEASE_URL, "application/vnd.github+json", ::validateMetadataUrl, check)
        try {
            if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) return null
            requireSuccess(connection)
            val declared = connection.contentLengthLong
            if (declared > MAX_UPDATE_METADATA_BYTES) throw IOException("Release metadata exceeded limits")
            val bytes = connection.inputStream.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    check()
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MAX_UPDATE_METADATA_BYTES) throw IOException("Release metadata exceeded limits")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            check()
            val text = try {
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            } catch (error: java.nio.charset.CharacterCodingException) { throw IOException("Invalid release metadata", error) }
            return selectGitHubRelease(parseMetadata(text))
        } finally { close(connection) }
    }

    override fun download(release: UpdateRelease, destination: File, onProgress: (Long, Long) -> Unit, checkCancelled: () -> Unit) {
        validateDownloadRelease(release)
        val token = generation.get()
        val check = { checkActive(token); checkCancelled() }
        check()
        val target = destination.absoluteFile
        val parent = target.parentFile ?: throw IOException("Update destination unavailable")
        if ((!parent.exists() && !parent.mkdirs()) || !parent.isDirectory || target.isDirectory) throw IOException("Update destination unavailable")
        if (parent.usableSpace < release.apkSize + 8L * 1024 * 1024) throw IOException("Insufficient update storage")
        val staged = File.createTempFile(".update-", ".part", parent)
        try {
            val connection = request(release.apkUrl, "application/octet-stream", { validateAssetRedirect(it, release) }, check)
            try {
                requireSuccess(connection)
                val declared = connection.contentLengthLong
                if (declared >= 0 && declared != release.apkSize) throw ApkValidationException(ApkValidationFailure.Payload)
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                onProgress(0, release.apkSize)
                connection.inputStream.use { input ->
                    FileOutputStream(staged).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            check()
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > release.apkSize || total > MAX_UPDATE_APK_BYTES) throw ApkValidationException(ApkValidationFailure.Payload)
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            onProgress(total, release.apkSize)
                        }
                        check()
                        output.fd.sync()
                    }
                }
                if (total != release.apkSize || !MessageDigest.isEqual(digest.digest(), release.sha256.hexBytes())) throw ApkValidationException(ApkValidationFailure.Payload)
                check()
                try {
                    Files.move(staged.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(staged.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } finally { close(connection) }
        } finally { staged.delete() }
    }

    override fun cancel() {
        generation.incrementAndGet()
        connections.forEach { runCatching { it.disconnect() } }
    }

    private fun checkActive(token: Long) {
        if (generation.get() != token || Thread.currentThread().isInterrupted) throw InterruptedIOException("Update operation cancelled")
    }

    private fun request(initial: String, accept: String, validate: (String) -> Unit, check: () -> Unit): HttpURLConnection {
        var url = initial
        repeat(6) { redirect ->
            validate(url)
            check()
            val connection = connector(URL(url))
            connections.add(connection)
            try {
                check()
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.requestMethod = "GET"
                connection.useCaches = false
                connection.setRequestProperty("Accept", accept)
                connection.setRequestProperty("Accept-Encoding", "identity")
                connection.setRequestProperty("User-Agent", "RiftDeck-Android-Updater")
                if (accept == "application/vnd.github+json") connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
                val status = connection.responseCode
                check()
                if (status !in setOf(301, 302, 303, 307, 308)) return connection
                if (redirect == 5) throw IOException("Too many update redirects")
                val location = connection.getHeaderField("Location") ?: throw IOException("Update redirect unavailable")
                if (location.length > 8192) throw IOException("Invalid update redirect")
                url = try { URI(url).resolve(location).toString() }
                catch (error: IllegalArgumentException) { throw IOException("Invalid update redirect", error) }
                validate(url)
                close(connection)
            } catch (error: Throwable) { close(connection); throw error }
        }
        throw IOException("Too many update redirects")
    }

    private fun requireSuccess(connection: HttpURLConnection) {
        if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("Update server returned HTTP ${connection.responseCode}")
    }

    private fun close(connection: HttpURLConnection) {
        connections.remove(connection)
        connection.disconnect()
    }

    private fun parseMetadata(text: String): ReleaseMetadata = try {
        validateStrictReleaseJson(text)
        val tokener = JSONTokener(text)
        val root = tokener.nextValue() as? JSONObject ?: throw IOException("Invalid release metadata")
        if (tokener.nextClean() != '\u0000') throw IOException("Unexpected release metadata")
        val assets = root.get("assets") as? JSONArray ?: throw IOException("Invalid release assets")
        if (assets.length() > 1000) throw IOException("Release metadata exceeded limits")
        ReleaseMetadata(
            root.string("tag_name"), root.boolean("draft"), root.boolean("prerelease"),
            if (!root.has("body") || root.isNull("body")) "" else root.string("body"), root.string("html_url"),
            List(assets.length()) { index ->
                val asset = assets.get(index) as? JSONObject ?: throw IOException("Invalid release asset")
                val size = asset.get("size")
                if (size !is Int && size !is Long) throw IOException("Invalid release asset size")
                ReleaseAsset(asset.string("name"), asset.string("browser_download_url"), (size as Number).toLong(),
                    if (!asset.has("digest") || asset.isNull("digest")) null else asset.string("digest"), asset.string("state"))
            },
        )
    } catch (error: JSONException) { throw IOException("Invalid release metadata", error) }

    private fun JSONObject.string(key: String): String = get(key) as? String ?: throw IOException("Invalid release field")
    private fun JSONObject.boolean(key: String): Boolean = get(key) as? Boolean ?: throw IOException("Invalid release field")
    private fun String.hexBytes(): ByteArray = ByteArray(length / 2) { index -> substring(index * 2, index * 2 + 2).toInt(16).toByte() }
}
