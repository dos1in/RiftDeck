package com.riftdeck.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.riftdeck.R
import com.riftdeck.core.input.controllerClickable
import com.riftdeck.core.ui.theme.LocalFrontendTheme

@Composable
internal fun DeckNavigationRail(
    section: DeckSection,
    rail: NavigationRailState,
    entryFocus: FocusRequester,
    requesters: Map<DeckSection, FocusRequester>,
    toggleFocus: FocusRequester,
    onNavigate: (DeckSection) -> Unit,
    onRailFocused: () -> Unit,
    hasGame: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalFrontendTheme.current
    val items = DeckSection.entries.filter { hasGame || it != DeckSection.Detail }
    val expansionState = stringResource(if (rail.expanded) R.string.navigation_expanded else R.string.navigation_collapsed)
    Column(modifier.background(colors.background).padding(horizontal = 6.dp, vertical = if (compact) 8.dp else 16.dp)) {
        NavigationRailItem(
            label = stringResource(if (rail.expanded) R.string.collapse_navigation else R.string.expand_navigation),
            displayLabel = stringResource(R.string.collapse_navigation_short),
            icon = if (rail.expanded) R.drawable.ic_nav_collapse else R.drawable.ic_nav_expand,
            expanded = rail.expanded,
            modifier = Modifier.focusRequester(toggleFocus).focusProperties {
                up = FocusRequester.Cancel; down = requesters.getValue(items.first())
                left = FocusRequester.Cancel; right = entryFocus
            }.semantics { stateDescription = expansionState },
            onFocused = onRailFocused,
            onClick = { toggleFocus.requestFocus(); rail.setExpanded(!rail.expanded) },
        )
        Spacer(Modifier.height(if (compact) 16.dp else 24.dp))
        items.forEachIndexed { index, item ->
            val label = stringResource(if (item == DeckSection.Detail) R.string.hint_details else item.title)
            NavigationRailItem(
                label = label,
                icon = when (item) {
                    DeckSection.Home -> R.drawable.ic_nav_home
                    DeckSection.Library -> R.drawable.ic_nav_library
                    DeckSection.Detail -> R.drawable.ic_nav_detail
                    DeckSection.Settings -> R.drawable.ic_nav_settings
                },
                expanded = rail.expanded,
                selected = item == section,
                modifier = Modifier.focusRequester(requesters.getValue(item)).focusProperties {
                    up = items.getOrNull(index - 1)?.let(requesters::getValue) ?: toggleFocus
                    down = items.getOrNull(index + 1)?.let(requesters::getValue) ?: FocusRequester.Cancel
                    left = FocusRequester.Cancel; right = entryFocus
                },
                onFocused = onRailFocused,
                onClick = {
                    requesters.getValue(item).requestFocus()
                    if (item != section) onNavigate(item)
                },
            )
            if (index < items.lastIndex) Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (rail.expanded) Arrangement.spacedBy(8.dp) else Arrangement.Center) {
            RiftDeckEmblem(Modifier.size(32.dp))
            if (rail.expanded) Text(stringResource(R.string.platform_gba_short), color = colors.textSecondary,
                style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun NavigationRailItem(
    label: String,
    @DrawableRes icon: Int,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    displayLabel: String = label,
    selected: Boolean = false,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = LocalFrontendTheme.current
    var focused by remember { mutableStateOf(false) }
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val foreground = if (selected) colors.accentText else colors.textPrimary
    Row(modifier.fillMaxWidth().heightIn(min = 44.dp)
        .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
        .background(if (focused || selected || hovered) colors.surfaceElevated else colors.background)
        .riftFrame(if (selected) colors.outline else Color.Transparent,
            colors.focusBorder, colors.secondary, focused, cut = 4.dp, accents = focused)
        .semantics { contentDescription = label; this.selected = selected }
        .hoverable(hoverSource)
        .controllerClickable(onClick = onClick)
        .padding(horizontal = if (expanded) 8.dp else 0.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.spacedBy(8.dp) else Arrangement.Center) {
        Icon(painterResource(icon), contentDescription = null, tint = foreground, modifier = Modifier.size(20.dp))
        if (expanded) Text(displayLabel, color = foreground, style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clearAndSetSemantics { })
    }
}
