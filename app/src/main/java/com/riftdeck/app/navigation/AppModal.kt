package com.riftdeck.app.navigation

/** Only the active modal owns controller input; lower-priority requests remain pending. */
internal enum class AppModal {
    ExternalError,
    PreparingGame,
    LaunchError,
    RemoveFolder,
    Search,
    Update,
}

internal fun activeAppModal(
    externalError: Boolean = false,
    preparingGame: Boolean = false,
    launchError: Boolean = false,
    removeFolder: Boolean = false,
    search: Boolean = false,
    update: Boolean = false,
): AppModal? = when {
    externalError -> AppModal.ExternalError
    preparingGame -> AppModal.PreparingGame
    launchError -> AppModal.LaunchError
    removeFolder -> AppModal.RemoveFolder
    search -> AppModal.Search
    update -> AppModal.Update
    else -> null
}
