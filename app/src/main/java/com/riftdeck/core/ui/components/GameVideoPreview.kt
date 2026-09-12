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
import kotlinx.coroutines.flow.first

val LocalPreviewScreenActive = staticCompositionLocalOf { true }
val LocalPreviewDelayMs = staticCompositionLocalOf { 650 }
val LocalLoopVideoPreviews = staticCompositionLocalOf { true }
val LocalVideoPreviews = staticCompositionLocalOf { false }

/** One muted preview for the active stage. Navigation and backgrounding dispose playback. */
@Composable
fun GameVideoPreview(uri: String?, modifier: Modifier = Modifier, onAspectRatio: (Float?) -> Unit = {},
    onFallback: () -> Unit = {},
) {
    if (!LocalVideoPreviews.current || uri == null) return
    val screenActive = LocalPreviewScreenActive.current
    val currentScreenActive by rememberUpdatedState(screenActive)
    val previewDelayMs = LocalPreviewDelayMs.current
    val loop = LocalLoopVideoPreviews.current
    val reportRatio by rememberUpdatedState(onAspectRatio)
    val reportFallback by rememberUpdatedState(onFallback)
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var player by remember(uri) { mutableStateOf<ExoPlayer?>(null) }
    var rendered by remember(uri) { mutableStateOf(false) }
    var ratio by remember(uri) { mutableFloatStateOf(1.5f) }
    LaunchedEffect(player, screenActive) {
        player?.let { active ->
            active.playWhenReady = screenActive
            if (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                android.util.Log.d("Media", "Preview active=$screenActive positionMs=${active.currentPosition}")
            }
        }
    }
    LaunchedEffect(uri, lifecycle, previewDelayMs, loop) {
        var position = 0L
        var completed = false
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (completed) return@repeatOnLifecycle
            snapshotFlow { currentScreenActive }.first { it }
            delay(previewDelayMs.toLong())
            val finished = CompletableDeferred<Unit>()
            val active = ExoPlayer.Builder(context).build()
            try {
                active.volume = 0f
                active.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                active.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) { completed = true; reportFallback(); finished.complete(Unit) }
                    }
                    override fun onRenderedFirstFrame() { rendered = true; reportRatio(ratio) }
                    override fun onVideoSizeChanged(size: VideoSize) {
                        if (size.height > 0) {
                            ratio = (size.width * size.pixelWidthHeightRatio / size.height).coerceIn(0.2f, 5f)
                            if (rendered) reportRatio(ratio)
                        }
                    }
                    override fun onPlayerError(error: PlaybackException) { rendered = false; reportRatio(null); reportFallback(); completed = true; finished.complete(Unit) }
                })
                player = active
                active.setMediaItem(MediaItem.fromUri(uri))
                active.seekTo(position)
                active.prepare()
                active.playWhenReady = currentScreenActive
                finished.await()
            } finally {
                position = active.currentPosition.coerceAtLeast(0L)
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
