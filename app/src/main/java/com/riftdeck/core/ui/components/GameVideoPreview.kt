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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

val LocalPreviewDelayMs = staticCompositionLocalOf { 650 }
val LocalLoopVideoPreviews = staticCompositionLocalOf { true }
val LocalVideoPreviews = staticCompositionLocalOf { false }

/** One muted preview for the active stage. Navigation and backgrounding dispose playback. */
@Composable
fun GameVideoPreview(uri: String?, modifier: Modifier = Modifier, onAspectRatio: (Float?) -> Unit = {}) {
    if (!LocalVideoPreviews.current || uri == null) return
    val previewDelayMs = LocalPreviewDelayMs.current
    val loop = LocalLoopVideoPreviews.current
    val reportRatio by rememberUpdatedState(onAspectRatio)
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var player by remember(uri) { mutableStateOf<ExoPlayer?>(null) }
    var rendered by remember(uri) { mutableStateOf(false) }
    var ratio by remember(uri) { mutableFloatStateOf(1.5f) }
    LaunchedEffect(uri, lifecycle, previewDelayMs, loop) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(previewDelayMs.toLong())
            val finished = CompletableDeferred<Unit>()
            val active = ExoPlayer.Builder(context).build()
            try {
                active.volume = 0f
                active.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                active.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) finished.complete(Unit)
                    }
                    override fun onRenderedFirstFrame() { rendered = true; reportRatio(ratio) }
                    override fun onVideoSizeChanged(size: VideoSize) {
                        if (size.height > 0) {
                            ratio = (size.width * size.pixelWidthHeightRatio / size.height).coerceIn(0.2f, 5f)
                            if (rendered) reportRatio(ratio)
                        }
                    }
                    override fun onPlayerError(error: PlaybackException) { rendered = false; reportRatio(null); finished.complete(Unit) }
                })
                player = active
                active.setMediaItem(MediaItem.fromUri(uri))
                active.prepare()
                active.playWhenReady = true
                finished.await()
            } finally {
                rendered = false
                reportRatio(null)
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
