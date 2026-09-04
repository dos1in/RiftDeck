package com.riftdeck.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
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
) {
    val colors = LocalFrontendTheme.current
    var focused by remember { mutableStateOf(false) }
    val shape = CutCornerShape(topEnd = 10.dp, bottomStart = 10.dp)
    val background = when {
        primary && focused -> colors.primary
        primary -> colors.surfaceElevated
        focused -> colors.surfaceElevated
        else -> colors.surface
    }
    val content = if (primary && focused) colors.background else colors.textPrimary
    val border = if (focused) colors.focusBorder else colors.outline

    Box(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusProperties {
                if (up != null) this.up = up
                if (down != null) this.down = down
                if (left != null) this.left = left
                if (right != null) this.right = right
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .background(background, shape)
            .border(width = if (focused) 3.dp else 1.dp, color = border, shape = shape)
            .heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .semantics { if (!enabled) disabled() }
            .controllerClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = content,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}
