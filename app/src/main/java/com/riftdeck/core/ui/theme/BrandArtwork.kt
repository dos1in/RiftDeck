package com.riftdeck.core.ui.theme

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import com.riftdeck.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class BrandArtwork(
    val darkEmblem: ImageBitmap,
    val darkWordmark: ImageBitmap,
)

val LocalBrandArtwork = staticCompositionLocalOf<BrandArtwork?> { null }

/** Share display-sized standalone brand assets without decoding on the UI thread. */
@Composable
internal fun rememberBrandArtwork(): BrandArtwork? {
    val resources = LocalResources.current
    val density = LocalDensity.current.density
    val artwork by produceState<BrandArtwork?>(null, resources, density) {
        value = withContext(Dispatchers.IO) {
            val emblem = decodeBrandImage(resources, R.drawable.riftdeck_emblem, 72 * density)
            val wordmark = decodeBrandImage(resources, R.drawable.riftdeck_wordmark, 144 * density)
            if (emblem != null && wordmark != null) {
                BrandArtwork(emblem, wordmark)
            } else {
                Log.w("Media", "Brand artwork unavailable")
                null
            }
        }
    }
    return artwork
}

private fun decodeBrandImage(
    resources: Resources,
    @DrawableRes resource: Int,
    targetWidth: Float,
): ImageBitmap? {
    val options = BitmapFactory.Options().apply {
        inScaled = false
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeResource(resources, resource, options)
    if (options.outWidth <= 0) return null
    var sample = 1
    while (options.outWidth / (sample * 2) >= targetWidth) sample *= 2
    options.inSampleSize = sample
    options.inJustDecodeBounds = false
    return BitmapFactory.decodeResource(resources, resource, options)?.asImageBitmap()
}
