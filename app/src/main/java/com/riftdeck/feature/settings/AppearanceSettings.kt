package com.riftdeck.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import com.riftdeck.R
import com.riftdeck.core.ui.components.NeonActionButton
import com.riftdeck.core.ui.theme.LocalFrontendTheme

@Composable
internal fun AppearanceSettings(
    language: String,
    onLanguage: (String) -> Unit,
    defaultHomeCategory: String,
    onDefaultHomeCategory: (String) -> Unit,
    reducedMotion: Boolean,
    onReducedMotion: (Boolean) -> Unit,
    controls: List<FocusRequester>,
    category: FocusRequester,
    onFocused: (Int) -> Unit,
) {
    val colors = LocalFrontendTheme.current
    NeonActionButton(stringResource(R.string.reduce_motion), { controls[0].requestFocus(); onReducedMotion(!reducedMotion) },
        Modifier.fillMaxWidth().semantics { role = Role.Switch; toggleableState = if (reducedMotion) ToggleableState.On else ToggleableState.Off },
        glyph = stringResource(if (reducedMotion) R.string.setting_on else R.string.setting_off), selected = reducedMotion,
        focusRequester = controls[0], left = category, down = controls[1], onFocused = { onFocused(0) })
    val choices = listOf("All", "Recent", "Favorites")
    val defaultLabel = stringResource(when (defaultHomeCategory) {
        "Recent" -> R.string.recent_title
        "Favorites" -> R.string.favorites_title
        else -> R.string.nav_home
    })
    NeonActionButton(stringResource(R.string.default_home_category, defaultLabel), {
        controls[1].requestFocus()
        onDefaultHomeCategory(choices[(choices.indexOf(defaultHomeCategory).coerceAtLeast(0) + 1) % choices.size])
    }, Modifier.fillMaxWidth(), focusRequester = controls[1], left = category, up = controls[0], down = controls[2],
        onFocused = { onFocused(1) })
    Text(stringResource(R.string.default_home_category_description), color = colors.textSecondary,
        style = MaterialTheme.typography.bodyMedium)
    val languageLabel = stringResource(when (language) {
        "zh-CN" -> R.string.language_chinese
        "en" -> R.string.language_english
        else -> R.string.language_system
    })
    NeonActionButton(stringResource(R.string.language_setting, languageLabel), {
        controls[2].requestFocus()
        val languages = listOf("system", "zh-CN", "en")
        onLanguage(languages[(languages.indexOf(language).coerceAtLeast(0) + 1) % languages.size])
    }, Modifier.fillMaxWidth(), focusRequester = controls[2], left = category, up = controls[1],
        onFocused = { onFocused(2) })
}
