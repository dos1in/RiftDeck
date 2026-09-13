package com.riftdeck.core.ui.theme

/** Recover alpha from a neon wordmark composited on black, including edge pixels. */
internal fun transparentWordmarkPixel(pixel: Int): Int {
    val red = (pixel ushr 16) and 255
    val green = (pixel ushr 8) and 255
    val blue = pixel and 255
    val brightness = maxOf(red, green, blue)
    if (brightness <= 8) return 0
    val alpha = ((pixel ushr 24) * brightness + 127) / 255
    return (alpha shl 24) or ((red * 255 / brightness) shl 16) or
        ((green * 255 / brightness) shl 8) or (blue * 255 / brightness)
}
