package com.riftdeck.core.update

import java.io.File

/** Stable versions use numeric comparison; prerelease/build labels are intentionally excluded. */
data class ReleaseVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<ReleaseVersion> {
    init { require(major >= 0 && minor >= 0 && patch >= 0) }
    override fun compareTo(other: ReleaseVersion): Int =
        major.compareTo(other.major).takeIf { it != 0 }
            ?: minor.compareTo(other.minor).takeIf { it != 0 }
            ?: patch.compareTo(other.patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val stable = Regex("[vV]?(0|[1-9][0-9]{0,9})\\.(0|[1-9][0-9]{0,9})\\.(0|[1-9][0-9]{0,9})")
        fun parse(value: String): ReleaseVersion? {
            val match = stable.matchEntire(value) ?: return null
            val components = match.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
            return ReleaseVersion(components[0], components[1], components[2])
        }
    }
}

data class UpdateRelease(
    val tag: String,
    val versionName: String,
    val version: ReleaseVersion,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String,
    val apkSize: Long,
    val sha256: String,
)

interface UpdateSource {
    /** Null means no published stable release with a compatible APK. */
    fun latest(): UpdateRelease?
    /** Download into private staging; publish destination only after length and digest validation. */
    fun download(release: UpdateRelease, destination: File, onProgress: (Long, Long) -> Unit, checkCancelled: () -> Unit)
    fun cancel() = Unit
}

const val MAX_UPDATE_APK_BYTES = 256L * 1024 * 1024
