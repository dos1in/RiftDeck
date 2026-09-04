package com.riftdeck.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.riftdeck.R
import com.riftdeck.core.input.GameAction
import com.riftdeck.core.input.controllerClickable
import com.riftdeck.core.input.gamepadActions
import com.riftdeck.core.ui.components.ControllerHint
import com.riftdeck.core.ui.components.ControllerHintBar
import com.riftdeck.core.ui.components.TerminalHeader
import com.riftdeck.core.ui.theme.LocalFrontendTheme

private val settingsSections = listOf(
    R.string.settings_library,
    R.string.settings_emulators,
    R.string.settings_appearance,
    R.string.settings_input,
    R.string.settings_video,
    R.string.settings_performance,
)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalFrontendTheme.current
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val requesters = remember { List(settingsSections.size) { FocusRequester() } }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        requesters[selectedIndex].requestFocus()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .gamepadActions { action ->
                when (action) {
                    GameAction.Back -> {
                        onBack()
                        true
                    }
                    GameAction.Up,
                    GameAction.Down,
                    GameAction.Left,
                    GameAction.Right,
                    GameAction.Confirm,
                    -> false
                    GameAction.Details,
                    GameAction.Favorite,
                    GameAction.PreviousCategory,
                    GameAction.NextCategory,
                    GameAction.Menu,
                    GameAction.Search,
                    -> true
                }
            },
    ) {
        val compact = maxHeight < 560.dp || maxWidth < 760.dp
        Column(Modifier.fillMaxSize()) {
            TerminalHeader(
                section = stringResource(R.string.settings_title),
                status = stringResource(R.string.status_ready),
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (compact) 16.dp else 28.dp,
                        vertical = if (compact) 12.dp else 20.dp,
                    ),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 24.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    settingsSections.forEachIndexed { index, titleRes ->
                        SettingsRow(
                            title = stringResource(titleRes),
                            selected = index == selectedIndex,
                            focusRequester = requesters[index],
                            up = requesters.getOrNull(index - 1),
                            down = requesters.getOrNull(index + 1),
                            onFocused = { selectedIndex = index },
                            onClick = { selectedIndex = index },
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                        .background(
                            colors.surface,
                            CutCornerShape(topEnd = 18.dp, bottomStart = 18.dp),
                        )
                        .border(
                            1.dp,
                            colors.outline,
                            CutCornerShape(topEnd = 18.dp, bottomStart = 18.dp),
                        )
                        .padding(if (compact) 16.dp else 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val selectedTitle = stringResource(settingsSections[selectedIndex])
                    Text(
                        text = selectedTitle,
                        color = colors.textPrimary,
                        style = if (compact) {
                            MaterialTheme.typography.headlineMedium
                        } else {
                            MaterialTheme.typography.headlineLarge
                        },
                    )
                    Text(
                        text = stringResource(R.string.settings_selected, selectedTitle),
                        color = colors.secondary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = stringResource(R.string.settings_summary),
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.settings_coming_soon),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            ControllerHintBar(
                hints = listOf(
                    ControllerHint(
                        "D-PAD",
                        stringResource(R.string.hint_navigate),
                        onClick = {
                            val nextIndex = (selectedIndex + 1) % settingsSections.size
                            selectedIndex = nextIndex
                            requesters[nextIndex].requestFocus()
                        },
                    ),
                    ControllerHint(
                        "A",
                        stringResource(R.string.hint_confirm),
                        onClick = { requesters[selectedIndex].requestFocus() },
                    ),
                    ControllerHint(
                        "B",
                        stringResource(R.string.hint_back),
                        onClick = onBack,
                    ),
                ),
            )
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    selected: Boolean,
    focusRequester: FocusRequester,
    up: FocusRequester?,
    down: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = LocalFrontendTheme.current
    var focused by remember { mutableStateOf(false) }
    val shape = CutCornerShape(topEnd = 8.dp, bottomStart = 8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .focusProperties {
                if (up != null) this.up = up
                if (down != null) this.down = down
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .background(
                when {
                    focused -> colors.surfaceElevated
                    selected -> colors.surface
                    else -> colors.background
                },
                shape,
            )
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = when {
                    focused -> colors.focusBorder
                    selected -> colors.secondary
                    else -> colors.outline
                },
                shape,
            )
            .controllerClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            color = if (focused) colors.primary else colors.textPrimary,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}
