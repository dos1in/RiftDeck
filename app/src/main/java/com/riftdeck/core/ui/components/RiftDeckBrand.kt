package com.riftdeck.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.riftdeck.R
import com.riftdeck.core.ui.theme.LocalBrandArtwork
import com.riftdeck.core.ui.theme.LocalFrontendTheme

@Composable
fun RiftDeckEmblem(modifier: Modifier = Modifier) {
    val artwork = LocalBrandArtwork.current
    val colors = LocalFrontendTheme.current
    val bounds = modifier.aspectRatio(430f / 342f)
    if (artwork != null) {
        Image(if (colors.isDark) artwork.darkEmblem else artwork.lightEmblem, stringResource(R.string.app_name), bounds,
            contentScale = ContentScale.Fit,
            colorFilter = if (colors.isDark) null else ColorFilter.tint(colors.background, BlendMode.Multiply))
    } else {
        Box(bounds)
    }
}

@Composable
fun RiftDeckWordmark(modifier: Modifier = Modifier) {
    val artwork = LocalBrandArtwork.current
    val colors = LocalFrontendTheme.current
    val bounds = modifier.width(144.dp).aspectRatio(664f / 140f)
    if (artwork != null) {
        Image(if (colors.isDark) artwork.darkWordmark else artwork.lightWordmark, stringResource(R.string.app_name), bounds,
            contentScale = ContentScale.Crop,
            colorFilter = if (colors.isDark) null else ColorFilter.tint(colors.background, BlendMode.Multiply))
    } else {
        Text(stringResource(R.string.app_name), modifier = bounds,
            style = MaterialTheme.typography.titleLarge, color = LocalFrontendTheme.current.primary)
    }
}
