package com.riftdeck.core.input

import android.view.KeyEvent
import android.content.res.Configuration
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.riftdeck.app.MainActivity
import com.riftdeck.core.ui.components.NeonActionButton
import com.riftdeck.core.ui.theme.RiftDeckTheme
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerInputWindowTest {
    @Test
    fun onlyFocusedWindowHandlesAnalogActionsAndControllerKeys() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val actions = MutableSharedFlow<GameAction>(extraBufferCapacity = 8)
        val showDialog = mutableStateOf(false)
        val backgroundFocused = AtomicBoolean()
        val dialogFocused = AtomicBoolean()
        val backgroundActions = AtomicInteger()
        val dialogActions = AtomicInteger()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // MainActivity rotates to landscape; install the fixture on the resulting activity.
            // Replacing content before that recreation would restore the production composition.
            waitUntil {
                var ready = false
                scenario.onActivity { activity ->
                    ready = activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                        activity.window.decorView.hasWindowFocus()
                }
                ready
            }
            scenario.onActivity { activity ->
                activity.setContent {
                    CompositionLocalProvider(LocalAnalogActions provides actions) {
                        RiftDeckTheme {
                            RecordWindowFocus(backgroundFocused)
                            val backgroundTarget = remember { FocusRequester() }
                            LaunchedEffect(Unit) { backgroundTarget.requestFocus() }
                            ControllerInput(enabled = true, modifier = Modifier.fillMaxSize(), onAction = {
                                backgroundActions.incrementAndGet()
                                true
                            }) {
                                NeonActionButton("Background", {}, focusRequester = backgroundTarget)
                            }
                            if (showDialog.value) Dialog(onDismissRequest = { showDialog.value = false }) {
                                RecordWindowFocus(dialogFocused)
                                val dialogTarget = remember { FocusRequester() }
                                LaunchedEffect(Unit) { dialogTarget.requestFocus() }
                                ControllerInput(enabled = true, onAction = {
                                    dialogActions.incrementAndGet()
                                    true
                                }) {
                                    NeonActionButton("Dialog", {}, focusRequester = dialogTarget)
                                }
                            }
                        }
                    }
                }
            }
            waitUntil(
                state = { "backgroundFocused=${backgroundFocused.get()}, subscribers=${actions.subscriptionCount.value}, lifecycle=${scenario.state}" },
            ) { backgroundFocused.get() && actions.subscriptionCount.value == 1 }
            assertTrue(actions.tryEmit(GameAction.Right))
            waitUntil { backgroundActions.get() == 1 }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BUTTON_Y)
            waitUntil { backgroundActions.get() == 2 }

            scenario.onActivity { showDialog.value = true }
            waitUntil(
                state = { "backgroundFocused=${backgroundFocused.get()}, dialogFocused=${dialogFocused.get()}, subscribers=${actions.subscriptionCount.value}" },
            ) { dialogFocused.get() && !backgroundFocused.get() && actions.subscriptionCount.value == 1 }
            assertTrue(actions.tryEmit(GameAction.Right))
            waitUntil { dialogActions.get() == 1 }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BUTTON_Y)
            waitUntil { dialogActions.get() == 2 }
            assertEquals(2, backgroundActions.get())

            scenario.onActivity { showDialog.value = false }
            waitUntil(
                state = { "backgroundFocused=${backgroundFocused.get()}, dialogFocused=${dialogFocused.get()}, subscribers=${actions.subscriptionCount.value}, lifecycle=${scenario.state}" },
            ) { backgroundFocused.get() && actions.subscriptionCount.value == 1 }
            assertTrue(actions.tryEmit(GameAction.Right))
            waitUntil { backgroundActions.get() == 3 }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BUTTON_Y)
            waitUntil { backgroundActions.get() == 4 }
            assertEquals(2, dialogActions.get())
        }
    }

    @Composable
    private fun RecordWindowFocus(state: AtomicBoolean) {
        val view = LocalView.current
        DisposableEffect(view) {
            val listener = android.view.ViewTreeObserver.OnWindowFocusChangeListener(state::set)
            val observer = view.viewTreeObserver
            observer.addOnWindowFocusChangeListener(listener)
            state.set(view.hasWindowFocus())
            onDispose {
                val currentObserver = if (observer.isAlive) observer else view.viewTreeObserver
                if (currentObserver.isAlive) currentObserver.removeOnWindowFocusChangeListener(listener)
                state.set(false)
            }
        }
    }

    private fun waitUntil(state: () -> String = { "" }, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(10)
        }
        assertTrue("Controller focus or input did not settle: ${state()}", condition())
    }
}
