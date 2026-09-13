package com.riftdeck.core.ui.theme

import android.content.res.Configuration
import android.os.LocaleList
import android.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources

/** Localize only this app; system configuration and other applications remain untouched. */
@Composable
fun AppLanguage(language: String, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val systemConfiguration = LocalConfiguration.current
    val configuration = remember(systemConfiguration, language) {
        Configuration(systemConfiguration).apply {
            if (language != "system") setLocales(LocaleList.forLanguageTags(language))
        }
    }
    val localized = remember(base, configuration) {
        ContextThemeWrapper(base, 0).apply { applyOverrideConfiguration(configuration) }
    }
    CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides configuration,
        LocalResources provides localized.resources, content = content)
}
