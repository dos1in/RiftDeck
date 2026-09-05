package com.riftdeck.core.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The logo's opposing cuts and yellow/cyan ends, without glow or continuous motion. */
fun Modifier.riftFrame(
    outline: Color,
    primary: Color,
    secondary: Color,
    focused: Boolean = false,
    cut: Dp = 12.dp,
    accents: Boolean = true,
): Modifier = drawWithCache {
    val width = (if (focused) 2.dp else 1.dp).toPx()
    val inset = width / 2
    val right = size.width - inset
    val bottom = size.height - inset
    val corner = cut.toPx().coerceAtMost(size.minDimension / 3)
    val segment = 28.dp.toPx().coerceAtMost(size.width / 4)
    val frame = Path().apply {
        moveTo(inset, inset); lineTo(right - corner, inset)
        lineTo(right, inset + corner); lineTo(right, bottom)
        lineTo(inset + corner, bottom); lineTo(inset, bottom - corner); close()
    }
    val leading = Path().apply {
        moveTo(inset, inset + 8.dp.toPx()); lineTo(inset, inset); lineTo(inset + segment, inset)
    }
    val trailing = Path().apply {
        moveTo(right - segment, bottom); lineTo(right, bottom); lineTo(right, bottom - 8.dp.toPx())
    }
    onDrawWithContent {
        drawContent()
        drawPath(frame, if (focused) primary else outline, style = Stroke(width))
        if (accents) {
            drawPath(leading, primary, style = Stroke(2.dp.toPx()))
            drawPath(trailing, secondary, style = Stroke(2.dp.toPx()))
        }
    }
}
