package com.riftdeck.core.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riftdeck.R
import com.riftdeck.core.input.ControllerInput
import com.riftdeck.core.input.GameAction
import com.riftdeck.core.input.controllerClickable
import com.riftdeck.core.ui.theme.LocalFrontendTheme
import java.util.Locale

enum class DeckSection(@param:StringRes val title: Int) {
    Home(R.string.nav_home), Library(R.string.settings_library),
    Detail(R.string.game_detail_title), Settings(R.string.settings_title),
}

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
    val requesters = remember { DeckSection.entries.associateWith { FocusRequester() } }
    val navItems = DeckSection.entries.filter { hasGame || it != DeckSection.Detail }
    ControllerInput(onAction, modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
            val compact = maxHeight < 520.dp || maxWidth < 900.dp
            val railWidth = (maxWidth * 0.12f).coerceIn(76.dp, 148.dp)
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier.width(railWidth).fillMaxHeight().background(colors.surface)
                        .padding(horizontal = if (compact) 8.dp else 14.dp, vertical = if (compact) 12.dp else 24.dp),
                ) {
                    Image(painterResource(R.drawable.ic_launcher), stringResource(R.string.app_name),
                        Modifier.size(if (compact) 36.dp else 48.dp).align(Alignment.CenterHorizontally))
                    if (!compact) Text(stringResource(R.string.brand_stacked), color = colors.textPrimary,
                        style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.weight(0.35f))
                    navItems.forEachIndexed { index, item ->
                        var focused by remember(item) { mutableStateOf(false) }
                        Row(
                            Modifier.fillMaxWidth().focusRequester(requesters.getValue(item))
                                .focusProperties {
                                    up = navItems.getOrNull(index - 1)?.let { requesters.getValue(it) } ?: FocusRequester.Cancel
                                    down = navItems.getOrNull(index + 1)?.let { requesters.getValue(it) } ?: FocusRequester.Cancel
                                    left = FocusRequester.Cancel; right = entryFocus
                                }
                                .onFocusChanged { focused = it.isFocused; if (it.isFocused) onRailFocused() }
                                .background(if (focused) colors.surfaceElevated else colors.surface)
                                .border(if (focused) 2.dp else 0.dp, if (focused) colors.primary else colors.surface)
                                .semantics { selected = item == section }
                                .controllerClickable { onNavigate(item) }
                                .padding(horizontal = 6.dp, vertical = if (compact) 10.dp else 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (!compact) Text(String.format(Locale.ROOT, "%02d", item.ordinal + 1),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (item == section) colors.primary else colors.textSecondary)
                            Text(stringResource(if (item == DeckSection.Detail) R.string.hint_details else item.title),
                                fontSize = 14.sp, fontWeight = if (item == section) FontWeight.Bold else FontWeight.Normal,
                                color = if (item == section) colors.primary else colors.textPrimary, maxLines = 1)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.width(28.dp).height(2.dp).background(colors.secondary))
                    Text(stringResource(R.string.platform_gba_short), color = colors.textPrimary,
                        style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
                    if (!compact) Text(stringResource(R.string.platform_gba), color = colors.textSecondary,
                        style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(colors.outline))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth().height(if (compact) 40.dp else 56.dp)
                        .padding(horizontal = if (compact) 16.dp else 30.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(String.format(Locale.ROOT, "%02d", section.ordinal + 1), color = colors.secondary,
                            style = MaterialTheme.typography.labelMedium)
                        Text(stringResource(R.string.app_name), color = colors.textPrimary, style = MaterialTheme.typography.labelLarge)
                        Text("/", color = colors.outline)
                        Text(stringResource(section.title), color = colors.textSecondary, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.weight(1f))
                        if (!compact) Text(stringResource(R.string.mock_library_label), color = colors.textSecondary,
                            style = MaterialTheme.typography.labelMedium)
                    }
                    HorizontalDivider(color = colors.outline)
                    Box(Modifier.weight(1f).fillMaxWidth()) { content(requesters.getValue(section), compact) }
                    HorizontalDivider(color = colors.outline)
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
                    }
                }
            }
        }
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
