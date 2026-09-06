package com.riftdeck.core.emulator

/** Configuration is platform-specific; launching is shared across platforms. */
data class EmulatorConfig(
    val platformId: Long,
    val packageName: String,
    val activityName: String,
    val displayName: String,
    val action: String = "android.intent.action.VIEW",
    val mimeType: String = "application/octet-stream",
    val extras: Map<String, String> = emptyMap(),
)

sealed interface LaunchResult {
    data object Started : LaunchResult
    data object NotConfigured : LaunchResult
    data object HistoryUnavailable : LaunchResult
    data object NotInstalled : LaunchResult
    data object InvalidConfiguration : LaunchResult
    data object RomUnavailable : LaunchResult
    data class InvalidArchive(val reason: ArchiveFailure) : LaunchResult
}
