package com.riftdeck.core.ui.theme

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.Bitmap
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
            val wordmark = decodeBrandImage(resources, R.drawable.riftdeck_wordmark, 144 * density, transparentBlack = true)
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
    transparentBlack: Boolean = false,
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
    val bitmap = BitmapFactory.decodeResource(resources, resource, options) ?: return null
    if (!transparentBlack) return bitmap.asImageBitmap()
    // The supplied wordmark is composited on black. Unmatte once after downsampling,
    // off the UI thread, to preserve antialiased neon edges on any app surface.
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    for (index in pixels.indices) pixels[index] = transparentWordmarkPixel(pixels[index])
    val transparent = Bitmap.createBitmap(pixels, bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    bitmap.recycle()
    return transparent.asImageBitmap()
}
