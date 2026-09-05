package com.riftdeck.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riftdeck.core.input.controllerClickable
import com.riftdeck.core.ui.theme.LocalFrontendTheme

@Composable
fun NeonActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    up: FocusRequester? = null,
    down: FocusRequester? = null,
    left: FocusRequester? = null,
    right: FocusRequester? = null,
    onFocused: () -> Unit = {},
    glyph: String? = null,
    selected: Boolean = false,
) {
    val colors = LocalFrontendTheme.current
    var focused by remember { mutableStateOf(false) }
    val shape = CutCornerShape(topEnd = if (primary) 12.dp else 0.dp)
    val contentColor = if (primary) colors.background else if (selected) colors.primary else colors.textPrimary
    Row(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusProperties {
                this.up = up ?: FocusRequester.Cancel
                this.down = down ?: FocusRequester.Cancel
                this.left = left ?: FocusRequester.Cancel
                this.right = right ?: FocusRequester.Cancel
            }
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
            .background(when { primary -> colors.primary; focused || selected -> colors.surfaceElevated; else -> colors.background }, shape)
            .border(if (focused) 2.dp else 1.dp, when { focused && primary -> colors.textPrimary; focused -> colors.focusBorder; primary -> colors.primary; else -> colors.outline }, shape)
            .heightIn(min = 42.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .semantics { this.selected = selected; if (!enabled) disabled() }
            .controllerClickable(enabled, onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = contentColor, style = MaterialTheme.typography.titleSmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (glyph != null) {
            Text(glyph, color = contentColor, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.border(1.dp, contentColor).padding(horizontal = 4.dp, vertical = 1.dp))
        }
    }
}
