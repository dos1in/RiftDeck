package com.riftdeck.core.sharing

import java.util.Locale

const val MAX_SHARED_SAVE_SIZE: Long = 64L * 1024 * 1024

private const val CONFLICT_MARKER = ".riftdeck-conflict-"
private val sha256Pattern = Regex("[0-9a-f]{64}")
private val numberedStateExtension = Regex("(?:state(?:[0-9]+|\\.auto)?|ss[0-9]{1,2}|st[0-9]{1,2})")
private val saveExtensions = setOf("sav", "srm", "rtc", "eep", "sra", "sgm", "fcs", "mcr", "mcd", "dsv", "sta")
private val temporaryDirectories = setOf("tmp", "temp", ".tmp", ".cache", "cache")

/** Paths are relative document names, never filesystem paths or document IDs. */
fun isSafeSharingPath(path: String): Boolean {
    if (path.isEmpty() || path.length > 1024 || path.startsWith('/') || path.endsWith('/')) return false
    if (path.any { it == '\\' || it == ':' || it.isISOControl() }) return false
    val segments = path.split('/')
    return segments.size <= 16 && segments.all { segment ->
        segment.isNotBlank() && segment != "." && segment != ".." &&
            segment.length <= 255 && segment.trim() == segment && !segment.endsWith('.')
    }
}

/** Conflict copies are retained for the user, but never treated as fresh emulator saves. */
fun isConflictSavePath(path: String): Boolean = path.substringAfterLast('/').contains(CONFLICT_MARKER)

fun isSupportedSavePath(path: String): Boolean {
    // Leave room for a conflict suffix even when the parent directories are unusually long.
    if (path.length > 960 || !isSafeSharingPath(path) || isConflictSavePath(path)) return false
    val segments = path.lowercase(Locale.ROOT).split('/')
    if (segments.dropLast(1).any { it in temporaryDirectories }) return false
    val fileName = segments.last()
    val extension = fileName.substringAfterLast('.', "")
    return extension in saveExtensions || numberedStateExtension.matches(extension) || fileName.endsWith(".state.auto")
}

data class SaveRevision(
    val path: String,
    val sha256: String,
    val size: Long,
    val modifiedAt: Long = 0,
) {
    init {
        require(isSafeSharingPath(path)) { "Unsafe save path" }
        require(sha256Pattern.matches(sha256)) { "Invalid SHA-256" }
        require(size in 0..MAX_SHARED_SAVE_SIZE) { "Invalid save size" }
    }
}

/** Both sides are retained separately after a conflict, until the user resolves it. */
data class SaveBaseline(val localSha256: String, val remoteSha256: String) {
    init {
        require(sha256Pattern.matches(localSha256) && sha256Pattern.matches(remoteSha256))
    }
}

sealed interface SaveSyncAction {
    val path: String

    data class Pull(val source: SaveRevision, val expectedLocalSha256: String?) : SaveSyncAction {
        override val path: String get() = source.path
    }

    data class Push(val source: SaveRevision, val expectedRemoteSha256: String?) : SaveSyncAction {
        override val path: String get() = source.path
    }

    /** Preserve both originals; copy remote into localCopyPath and local into remoteCopyPath. */
    data class Conflict(val local: SaveRevision, val remote: SaveRevision) : SaveSyncAction {
        override val path: String get() = local.path
        val localCopyPath: String get() = conflictSavePath(path, remote.sha256)
        val remoteCopyPath: String get() = conflictSavePath(path, local.sha256)

        init { require(local.path == remote.path && local.sha256 != remote.sha256) }
    }
}

data class SaveSyncPlan(
    val actions: List<SaveSyncAction>,
    /** Already observed equal/stable revisions, plus old baselines for unacknowledged actions. */
    val baselines: Map<String, SaveBaseline>,
)

/** Content hashes decide changes. Device clocks and file timestamps never choose a winner. */
fun planSaveSync(
    local: List<SaveRevision>,
    remote: List<SaveRevision>,
    baselines: Map<String, SaveBaseline>,
): SaveSyncPlan {
    fun canonicalIndex(revisions: List<SaveRevision>): Map<String, SaveRevision> {
        val canonical = revisions.filter { isSupportedSavePath(it.path) }
        require(canonical.map { it.path }.distinct().size == canonical.size) { "Duplicate save paths" }
        return canonical.associateBy { it.path }
    }

    val localByPath = canonicalIndex(local)
    val remoteByPath = canonicalIndex(remote)
    val pathSet = localByPath.keys + remoteByPath.keys
    val paths = pathSet.sorted()
    val nextBaselines = baselines.filterKeys { it in pathSet }.toMutableMap()
    val actions = paths.mapNotNull { path ->
        val ours = localByPath[path]
        val theirs = remoteByPath[path]
        val previous = baselines[path]
        when {
            ours == null -> SaveSyncAction.Pull(requireNotNull(theirs), expectedLocalSha256 = null)
            theirs == null -> SaveSyncAction.Push(ours, expectedRemoteSha256 = null)
            ours.sha256 == theirs.sha256 -> {
                nextBaselines[path] = SaveBaseline(ours.sha256, theirs.sha256)
                null
            }
            previous == null -> SaveSyncAction.Conflict(ours, theirs)
            ours.sha256 == previous.localSha256 && theirs.sha256 == previous.remoteSha256 -> null
            previous.localSha256 != previous.remoteSha256 -> SaveSyncAction.Conflict(ours, theirs)
            ours.sha256 == previous.localSha256 -> SaveSyncAction.Pull(theirs, ours.sha256)
            theirs.sha256 == previous.remoteSha256 -> SaveSyncAction.Push(ours, theirs.sha256)
            else -> SaveSyncAction.Conflict(ours, theirs)
        }
    }
    return SaveSyncPlan(actions, nextBaselines.toMap())
}

/**
 * Call only after re-reading canonical files and verifying completed copies. A failed or stale
 * transfer cannot advance the baseline. For conflicts, both hash-named copies must be verified.
 */
fun acknowledgedSaveBaseline(
    action: SaveSyncAction,
    localSha256: String?,
    remoteSha256: String?,
    localConflictSha256: String? = null,
    remoteConflictSha256: String? = null,
): SaveBaseline? = when (action) {
    is SaveSyncAction.Pull -> if (localSha256 == action.source.sha256 && remoteSha256 == action.source.sha256) {
        SaveBaseline(action.source.sha256, action.source.sha256)
    } else null
    is SaveSyncAction.Push -> if (localSha256 == action.source.sha256 && remoteSha256 == action.source.sha256) {
        SaveBaseline(action.source.sha256, action.source.sha256)
    } else null
    is SaveSyncAction.Conflict -> if (localSha256 == action.local.sha256 && remoteSha256 == action.remote.sha256 &&
        localConflictSha256 == action.remote.sha256 && remoteConflictSha256 == action.local.sha256
    ) {
        SaveBaseline(action.local.sha256, action.remote.sha256)
    } else null
}

/** Deterministic names make interrupted/repeated transfers reuse the same preserved version. */
fun conflictSavePath(path: String, sha256: String): String {
    require(isSafeSharingPath(path) && sha256Pattern.matches(sha256))
    val directory = path.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
    val fileName = path.substringAfterLast('/')
    // .state.auto is one extension for emulator state naming purposes.
    val extension = if (fileName.lowercase(Locale.ROOT).endsWith(".state.auto")) {
        fileName.takeLast(11)
    } else {
        fileName.lastIndexOf('.').takeIf { it > 0 }?.let { fileName.substring(it) }.orEmpty()
    }
    val suffix = "$CONFLICT_MARKER${sha256.take(12)}$extension"
    val stem = fileName.dropLast(extension.length)
    var stemLength = minOf(stem.length, 255 - suffix.length, 1024 - directory.length - suffix.length)
    require(stemLength > 0) { "Save path has no space for conflict suffix" }
    if (stem[stemLength - 1].isHighSurrogate()) stemLength--
    val result = "$directory${stem.take(stemLength)}$suffix"
    require(isSafeSharingPath(result))
    return result
}
