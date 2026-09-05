package com.riftdeck.core.model

enum class ThemeMode(val storageValue: String) {
    System("system"), Light("light"), Dark("dark");

    fun isDark(systemIsDark: Boolean): Boolean = when (this) {
        System -> systemIsDark
        Light -> false
        Dark -> true
    }

    companion object {
        fun fromStorage(value: String?): ThemeMode = entries.firstOrNull { it.storageValue == value } ?: System
    }
}

enum class ThemePalette(val storageValue: String) {
    Rift("rift"), Ocean("ocean"), Ember("ember");

    companion object {
        fun fromStorage(value: String?): ThemePalette = entries.firstOrNull { it.storageValue == value } ?: Rift
    }
}
