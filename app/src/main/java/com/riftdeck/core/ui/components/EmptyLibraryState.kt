package com.riftdeck.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.riftdeck.core.ui.theme.LocalFrontendTheme

@Composable
fun EmptyLibraryState(
    title: String,
    message: String,
    actionLabel: String,
    focusRequester: FocusRequester,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    left: FocusRequester = FocusRequester.Cancel,
) {
    val colors = LocalFrontendTheme.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Canvas(
            modifier = Modifier
                .width(54.dp)
                .height(42.dp)
                .clearAndSetSemantics { },
        ) {
            drawRect(
                color = colors.outline,
                topLeft = Offset(size.width * 0.08f, size.height * 0.08f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.84f, size.height * 0.84f),
                style = Stroke(width = 3.dp.toPx()),
            )
            drawLine(
                color = colors.primary,
                start = Offset(size.width * 0.28f, size.height * 0.5f),
                end = Offset(size.width * 0.72f, size.height * 0.5f),
                strokeWidth = 3.dp.toPx(),
            )
        }
        Text(
            text = title,
            color = colors.textPrimary,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        NeonActionButton(
            label = actionLabel,
            onClick = onAction,
            focusRequester = focusRequester,
            primary = true,
            left = left,
        )
    }
}
