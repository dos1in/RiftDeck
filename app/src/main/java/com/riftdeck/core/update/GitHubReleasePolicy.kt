package com.riftdeck.core.update

import java.io.IOException
import java.net.URI
import java.util.Locale

internal data class ReleaseAsset(val name: String, val url: String, val size: Long, val digest: String?, val state: String = "uploaded")
internal data class ReleaseMetadata(
    val tag: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val notes: String,
    val pageUrl: String,
    val assets: List<ReleaseAsset>,
)

internal const val UPDATE_OWNER = "dos1in"
internal const val UPDATE_REPOSITORY = "RiftDeck"
internal const val LATEST_RELEASE_URL = "https://api.github.com/repos/$UPDATE_OWNER/$UPDATE_REPOSITORY/releases/latest"
internal const val MAX_UPDATE_METADATA_BYTES = 1024 * 1024
private val apkName = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,159}\\.apk", RegexOption.IGNORE_CASE)
private val architecture = Regex("(?:^|[-_.])(?:arm64(?:-v8a)?|armv7|arm|armeabi(?:-v7a)?|aarch64|x86(?:_64)?)(?:[-_.]|$)", RegexOption.IGNORE_CASE)
private val digestFormat = Regex("sha256:([0-9a-fA-F]{64})")

/** Prefer an explicitly named universal release; an ambiguous collection must not pick by array order. */
internal fun selectGitHubRelease(metadata: ReleaseMetadata): UpdateRelease? {
    if (metadata.draft || metadata.prerelease) return null
    val version = ReleaseVersion.parse(metadata.tag) ?: return null
    if (metadata.notes.length > MAX_UPDATE_METADATA_BYTES || metadata.assets.size > 1000) throw IOException("Release metadata exceeded limits")
    validateReleasePage(metadata.pageUrl, metadata.tag)
    val candidates = metadata.assets.filter { asset ->
        apkName.matches(asset.name) && asset.state == "uploaded" &&
            !asset.name.contains("debug", true) && !asset.name.contains("unsigned", true) &&
            !architecture.containsMatchIn(asset.name)
    }
    if (candidates.isEmpty()) return null
    val preferred = listOf("RiftDeck-${metadata.tag}.apk", "RiftDeck-$version.apk", "RiftDeck.apk", "app-release.apk")
        .distinct().firstNotNullOfOrNull { name ->
            candidates.filter { it.name.equals(name, true) }.let { matching ->
                if (matching.size > 1) throw IOException("Ambiguous release APK")
                matching.singleOrNull()
            }
        }
    val asset = preferred ?: candidates.singleOrNull() ?: throw IOException("Ambiguous release APK")
    if (asset.size !in 1..MAX_UPDATE_APK_BYTES) throw IOException("Invalid APK size")
    val sha256 = digestFormat.matchEntire(asset.digest.orEmpty())?.groupValues?.get(1)?.lowercase(Locale.ROOT)
        ?: throw IOException("Release APK has no SHA-256 digest")
    validateApkUrl(asset.url, metadata.tag, asset.name)
    return UpdateRelease(metadata.tag, version.toString(), version, metadata.notes, metadata.pageUrl, asset.url, asset.size, sha256)
}

internal fun validateReleasePage(url: String, tag: String) {
    val uri = secureUri(url)
    if (uri.host.equals("github.com", true).not() || uri.rawQuery != null ||
        !matchesRepositoryPath(uri.rawPath, "/releases/tag/$tag")) throw IOException("Invalid release page")
}

internal fun validateApkUrl(url: String, tag: String, name: String) {
    if (ReleaseVersion.parse(tag) == null || !apkName.matches(name)) throw IOException("Invalid APK release path")
    val uri = secureUri(url)
    if (!uri.host.equals("github.com", true) || uri.rawQuery != null ||
        !matchesRepositoryPath(uri.rawPath, "/releases/download/$tag/$name")) throw IOException("Invalid APK URL")
}

internal fun validateMetadataUrl(url: String) {
    val uri = secureUri(url)
    if (!uri.host.equals("api.github.com", true) || uri.rawQuery != null ||
        !uri.rawPath.equals("/repos/$UPDATE_OWNER/$UPDATE_REPOSITORY/releases/latest", true)) throw IOException("Invalid update source")
}

internal fun validateAssetRedirect(url: String, release: UpdateRelease) {
    val uri = secureUri(url)
    when (uri.host.lowercase(Locale.ROOT)) {
        "github.com" -> validateApkUrl(url, release.tag, secureUri(release.apkUrl).rawPath.substringAfterLast('/'))
        "release-assets.githubusercontent.com", "objects.githubusercontent.com", "github-releases.githubusercontent.com" -> Unit
        else -> throw IOException("Untrusted APK redirect")
    }
}

internal fun validateDownloadRelease(release: UpdateRelease) {
    if (ReleaseVersion.parse(release.tag) != release.version || release.versionName != release.version.toString() ||
        release.apkSize !in 1..MAX_UPDATE_APK_BYTES || !release.sha256.matches(Regex("[0-9a-f]{64}"))) throw IOException("Invalid update release")
    validateReleasePage(release.pageUrl, release.tag)
    validateApkUrl(release.apkUrl, release.tag, secureUri(release.apkUrl).rawPath.substringAfterLast('/'))
}

private fun matchesRepositoryPath(path: String?, suffix: String): Boolean {
    val prefix = "/$UPDATE_OWNER/$UPDATE_REPOSITORY"
    return path != null && path.length == prefix.length + suffix.length &&
        path.regionMatches(0, prefix, 0, prefix.length, ignoreCase = true) && path.substring(prefix.length) == suffix
}

private fun secureUri(url: String): URI {
    if (url.length !in 1..8192) throw IOException("Invalid update URL")
    val uri = try { URI(url) } catch (error: java.net.URISyntaxException) { throw IOException("Invalid update URL", error) }
    if (!uri.scheme.equals("https", true) || uri.host.isNullOrBlank() || uri.rawUserInfo != null ||
        uri.rawFragment != null || uri.port !in listOf(-1, 443)) throw IOException("Update URL requires HTTPS")
    return uri
}
