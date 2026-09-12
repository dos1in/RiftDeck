package com.riftdeck.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.riftdeck.R
import com.riftdeck.core.model.Game
import com.riftdeck.core.ui.theme.LocalFrontendTheme

@Composable
fun GameArtwork(
    game: Game,
    contentDescription: String,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    onAspectRatio: (Float) -> Unit = {},
) {
    val colors = LocalFrontendTheme.current
    val context = LocalContext.current
    val coverRequest = remember(context, game.coverUri, game.coverVersion) {
        game.coverUri?.let { uri -> ImageRequest.Builder(context).data(uri)
            .memoryCacheKey("$uri:${game.coverVersion}").diskCacheKey("$uri:${game.coverVersion}").build() }
    }
    var coverLoaded by remember(coverRequest) { mutableStateOf(false) }
    val accent = when ((game.id % 3).toInt()) {
        0 -> colors.primary
        1 -> colors.secondary
        else -> colors.tertiary
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .background(colors.surfaceElevated)
            .semantics { this.contentDescription = contentDescription },
    ) {
        if (!coverLoaded) Canvas(Modifier.fillMaxSize()) {
            if (size.minDimension <= 0f) return@Canvas
            val gridColor = colors.outline.copy(alpha = 0.46f)
            val step = size.minDimension / 6f
            var position = step
            while (position < size.maxDimension) {
                drawLine(gridColor, Offset(position, 0f), Offset(position, size.height), 1f)
                drawLine(gridColor, Offset(0f, position), Offset(size.width, position), 1f)
                position += step
            }
            drawRect(
                color = accent.copy(alpha = 0.68f),
                topLeft = Offset(size.width * 0.14f, size.height * 0.16f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.23f, size.height * 0.23f),
            )
            drawCircle(
                color = accent.copy(alpha = 0.9f),
                radius = size.minDimension * 0.18f,
                center = Offset(size.width * 0.7f, size.height * 0.38f),
                style = Stroke(width = size.minDimension * 0.025f),
            )
            drawLine(
                color = accent,
                start = Offset(size.width * 0.12f, size.height * 0.78f),
                end = Offset(size.width * 0.86f, size.height * 0.62f),
                strokeWidth = size.minDimension * 0.035f,
            )
        }
        if (coverRequest != null) AsyncImage(
            model = coverRequest, contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
            onSuccess = {
                coverLoaded = true
                val image = it.result.image
                if (image.width > 0 && image.height > 0) onAspectRatio(image.width.toFloat() / image.height)
            }, onError = { coverLoaded = false },
        )
        if (showLabel && !coverLoaded) Text(
            text = stringResource(R.string.cover_placeholder),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
            color = colors.textPrimary,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
        )
    }
}
