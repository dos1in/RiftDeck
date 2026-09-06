package com.riftdeck.core.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.riftdeck.R
import com.riftdeck.core.input.ControllerInput
import com.riftdeck.core.input.GameAction
import com.riftdeck.core.ui.theme.LocalFrontendTheme
import java.util.Locale

enum class DeckSection(@param:StringRes val title: Int) {
    Home(R.string.nav_home), Library(R.string.settings_library),
    Detail(R.string.game_detail_title), Settings(R.string.settings_title),
}

data class NavigationRailState(val expanded: Boolean = true, val setExpanded: (Boolean) -> Unit = {})

val LocalNavigationRail = compositionLocalOf { NavigationRailState() }

@Composable
fun DeckScaffold(
    section: DeckSection,
    entryFocus: FocusRequester,
    onNavigate: (DeckSection) -> Unit,
    onAction: (GameAction) -> Boolean,
    modifier: Modifier = Modifier,
    hasGame: Boolean = true,
    onRailFocused: () -> Unit = {},
    content: @Composable (railFocus: FocusRequester, compact: Boolean) -> Unit,
) {
    val colors = LocalFrontendTheme.current
    val rail = LocalNavigationRail.current
    val toggleFocus = remember { FocusRequester() }
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val requesters = remember { DeckSection.entries.associateWith { FocusRequester() } }
    ControllerInput(onAction, modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
            val compact = maxHeight < 520.dp || maxWidth < 900.dp
            val railWidth = if (rail.expanded) (maxWidth * 0.16f).coerceAtLeast(112.dp * fontScale).coerceAtMost(168.dp) else 56.dp
            Row(Modifier.fillMaxSize()) {
                DeckNavigationRail(section, rail, entryFocus, requesters, toggleFocus, onNavigate, onRailFocused,
                    hasGame, compact, Modifier.width(railWidth).fillMaxHeight())
                Box(Modifier.width(1.dp).fillMaxHeight().background(colors.outline))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth().height(if (compact) 40.dp else 56.dp)
                        .padding(horizontal = if (compact) 16.dp else 30.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(String.format(Locale.ROOT, "%02d", section.ordinal + 1), color = colors.secondary,
                            style = MaterialTheme.typography.labelMedium)
                        RiftDeckWordmark()
                        Text("/", color = colors.outline)
                        Text(stringResource(section.title), color = colors.textSecondary, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.weight(1f))
                        if (!compact) Text(stringResource(R.string.local_library_label), color = colors.textSecondary,
                            style = MaterialTheme.typography.labelMedium)
                    }
                    DeckDivider()
                    Box(Modifier.weight(1f).fillMaxWidth()) { content(requesters.getValue(section), compact) }
                    DeckDivider()
                    Row(Modifier.fillMaxWidth().height(if (compact) 36.dp else 44.dp)
                        .padding(horizontal = if (compact) 12.dp else 30.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 22.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        ControllerHintGlyph("A", stringResource(R.string.hint_confirm))
                        ControllerHintGlyph("B", stringResource(R.string.hint_back))
                        if (hasGame && section != DeckSection.Settings) {
                            if (section != DeckSection.Detail) ControllerHintGlyph("X", stringResource(R.string.hint_details))
                            ControllerHintGlyph("Y", stringResource(R.string.hint_favorite))
                        }
                        if (!compact && section != DeckSection.Detail) ControllerHintGlyph("L1/R1",
                            stringResource(if (section == DeckSection.Home) R.string.hint_games else R.string.hint_categories))
                        if (!compact && section != DeckSection.Settings) {
                            Spacer(Modifier.weight(1f))
                            ControllerHintGlyph("Start", stringResource(R.string.settings_title))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeckDivider() {
    val colors = LocalFrontendTheme.current
    Row(Modifier.fillMaxWidth().height(1.dp)) {
        Box(Modifier.width(24.dp).fillMaxHeight().background(colors.primary))
        Box(Modifier.weight(1f).fillMaxHeight().background(colors.outline))
        Box(Modifier.width(24.dp).fillMaxHeight().background(colors.secondary))
    }
}

@Composable
private fun ControllerHintGlyph(key: String, label: String) {
    val colors = LocalFrontendTheme.current
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(key, color = colors.textPrimary, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.border(1.dp, colors.textSecondary).padding(horizontal = 4.dp, vertical = 1.dp))
        Text(label, color = colors.textSecondary, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun DeckHeading(@StringRes title: Int, @StringRes eyebrow: Int, compact: Boolean, trailing: @Composable () -> Unit = {}) {
    val colors = LocalFrontendTheme.current
    Row(Modifier.fillMaxWidth().padding(bottom = if (compact) 10.dp else 20.dp),
        verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!compact) Text(stringResource(eyebrow), color = colors.secondary, style = MaterialTheme.typography.labelMedium)
            Text(stringResource(title), color = colors.textPrimary,
                style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge)
        }
        trailing()
    }
}
