package com.riftdeck.feature.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.riftdeck.R
import com.riftdeck.core.ui.theme.LocalFrontendTheme
import com.riftdeck.core.ui.theme.LocalReducedMotion
import com.riftdeck.data.scanner.ScanState

@Composable
internal fun ScanStatus(scan: ScanState) {
    val colors = LocalFrontendTheme.current
    val reducedMotion = LocalReducedMotion.current
    val opacity = remember { Animatable(1f) }
    val feedback = remember { ScanCompletionFeedback(scan) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentScan by rememberUpdatedState(scan)
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        var wasPaused = false
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                wasPaused = true
                feedback.skip(currentScan)
                resumed = false
            } else if (event == Lifecycle.Event.ON_RESUME) {
                if (wasPaused) feedback.skip(currentScan)
                resumed = true
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // Counts can update many times per scan. Only terminal state changes drive this effect.
    LaunchedEffect(scan.runId, scan.running, scan.completed, scan.failedFolders.isEmpty(), reducedMotion, resumed) {
        val animate = feedback.consume(scan)
        if (animate && resumed) {
            opacity.snapTo(if (reducedMotion) 0.85f else 0.65f)
            opacity.animateTo(1f, tween(if (reducedMotion) 80 else 160, easing = FastOutSlowInEasing))
        } else {
            opacity.snapTo(1f)
        }
    }
    Text(stringResource(when {
        scan.running -> R.string.scan_running
        scan.failedFolders.isNotEmpty() -> R.string.scan_failed
        scan.completed -> R.string.scan_complete
        else -> R.string.scan_ready
    }, scan.discovered), color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.graphicsLayer { alpha = opacity.value })
}
