@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.riftdeck.core.ui.components

import android.view.TextureView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

val LocalVideoPreviews = staticCompositionLocalOf { false }

/** One muted preview for the active stage. Navigation and backgrounding dispose playback. */
@Composable
fun GameVideoPreview(uri: String?, modifier: Modifier = Modifier) {
    if (!LocalVideoPreviews.current || uri == null) return
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var player by remember(uri) { mutableStateOf<ExoPlayer?>(null) }
    var rendered by remember(uri) { mutableStateOf(false) }
    var ratio by remember(uri) { mutableFloatStateOf(1.5f) }
    LaunchedEffect(uri, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(650)
            val active = ExoPlayer.Builder(context).build()
            try {
                active.volume = 0f
                active.repeatMode = Player.REPEAT_MODE_ONE
                active.addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() { rendered = true }
                    override fun onVideoSizeChanged(size: VideoSize) {
                        if (size.height > 0) ratio = (size.width * size.pixelWidthHeightRatio / size.height).coerceIn(0.2f, 5f)
                    }
                    override fun onPlayerError(error: PlaybackException) { rendered = false; active.stop() }
                })
                player = active
                active.setMediaItem(MediaItem.fromUri(uri))
                active.prepare()
                active.playWhenReady = true
                awaitCancellation()
            } finally {
                rendered = false
                player = null
                active.release()
            }
        }
    }
    val active = player
    if (active != null) BoxWithConstraints(modifier.clipToBounds().background(if (rendered) Color.Black else Color.Transparent), contentAlignment = Alignment.Center) {
        val width = minOf(maxWidth, maxHeight * ratio)
        AndroidView(factory = { TextureView(it).apply { isFocusable = false; isClickable = false } },
            update = { active.setVideoTextureView(it) },
            modifier = Modifier.size(width, width / ratio).graphicsLayer { alpha = if (rendered) 1f else 0f })
    }
}
