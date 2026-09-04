package com.riftdeck.app

import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.riftdeck.app.navigation.RiftDeckApp
import com.riftdeck.core.input.GamepadInputManager
import com.riftdeck.core.ui.theme.RiftDeckTheme
import com.riftdeck.feature.home.HomeViewModel
import com.riftdeck.feature.home.HomeViewModelFactory

class MainActivity : ComponentActivity() {
    private val gamepadInputManager = GamepadInputManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )

        setContent {
            val repository = (application as LauncherApplication).gameRepository
            val factory = remember(repository) { HomeViewModelFactory(repository) }
            val homeViewModel: HomeViewModel = viewModel(factory = factory)

            RiftDeckTheme {
                RiftDeckApp(
                    homeViewModel = homeViewModel,
                    analogActions = gamepadInputManager.actions,
                    onExit = ::finish,
                )
            }
        }
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        gamepadInputManager.onGenericMotionEvent(event) || super.dispatchGenericMotionEvent(event)
}
