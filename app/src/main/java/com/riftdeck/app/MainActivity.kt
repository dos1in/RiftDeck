package com.riftdeck.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riftdeck.core.media.PreviewScreenMonitor
import com.riftdeck.core.ui.components.LocalPreviewScreenActive
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.riftdeck.app.navigation.RiftDeckApp
import com.riftdeck.core.input.GamepadInputManager
import com.riftdeck.core.launcher.HomeLauncher
import com.riftdeck.core.ui.theme.RiftDeckTheme
import com.riftdeck.feature.home.HomeViewModel
import com.riftdeck.feature.home.HomeViewModelFactory
import com.riftdeck.feature.settings.LibraryViewModel
import com.riftdeck.feature.emulator.EmulatorViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class MainActivity : ComponentActivity() {
    private val previewScreen by lazy { PreviewScreenMonitor(this) }
    private val gamepadInputManager = GamepadInputManager()

    private val homeLauncher by lazy { HomeLauncher(this) }
    private var defaultHome by mutableStateOf(false)
    private var launcherError by mutableStateOf(false)
    private val homeRequests = Channel<Unit>(Channel.CONFLATED)
    private val homeEvents = homeRequests.receiveAsFlow()
    private val requestHome = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        defaultHome = homeLauncher.isDefault()
    }

    private fun chooseHome() {
        try {
            val request = homeLauncher.requestIntent()
            if (request != null) requestHome.launch(request)
            else launcherError = !homeLauncher.openSettings(home = true)
        } catch (_: ActivityNotFoundException) {
            launcherError = !homeLauncher.openSettings(home = true)
        } catch (_: SecurityException) {
            launcherError = !homeLauncher.openSettings(home = true)
        }
    }

    override fun onResume() {
        super.onResume()
        previewScreen.resume()
        defaultHome = homeLauncher.isDefault()
        (application as LauncherApplication).emulationRepository.onFrontendResumed()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            homeLauncher.removeOtherLauncherTasks(taskId, componentName)
            launcherError = false
            homeRequests.trySend(Unit)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            homeLauncher.removeOtherLauncherTasks(taskId, componentName)
        } else if (homeLauncher.isDefault()) {
            // App-icon launches also enter the system HOME task once RiftDeck owns the role.
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setComponent(componentName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finishAndRemoveTask()
            return
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        setContent {
            val launcher = application as LauncherApplication
            val repository = launcher.gameRepository
            val factory = remember(repository) { HomeViewModelFactory(repository, launcher.uiPreferencesRepository) }
            val homeViewModel: HomeViewModel = viewModel(factory = factory)

            val libraryFactory = remember(launcher) { LibraryViewModel.Factory(launcher.libraryRepository) }
            val libraryViewModel: LibraryViewModel = viewModel(factory = libraryFactory)
            val emulatorFactory = remember(launcher) { EmulatorViewModel.Factory(launcher.emulationRepository, launcher.uiPreferencesRepository) }
            val emulatorViewModel: EmulatorViewModel = viewModel(factory = emulatorFactory)
            val screenActive by previewScreen.active.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalPreviewScreenActive provides screenActive) {
            RiftDeckTheme() {
                RiftDeckApp(
                    homeViewModel = homeViewModel,
                    libraryViewModel = libraryViewModel,
                    emulatorViewModel = emulatorViewModel,
                    analogActions = gamepadInputManager.actions,
                    onExit = {
                        if (!defaultHome && !intent.hasCategory(Intent.CATEGORY_HOME)) finish()
                    },
                    homeRequests = homeEvents,
                    isDefaultHome = defaultHome,
                    onChooseHome = ::chooseHome,
                    onSystemSettings = { launcherError = !homeLauncher.openSettings(home = false) },
                    launcherError = launcherError,
                    onDismissLauncherError = { launcherError = false },
                )
            }
        }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        previewScreen.userActivity()
    }

    override fun onDestroy() {
        previewScreen.destroy()
        super.onDestroy()
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val handled = gamepadInputManager.onGenericMotionEvent(event)
        if (handled) previewScreen.userActivity()
        return handled || super.dispatchGenericMotionEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        previewScreen.windowFocusChanged(hasFocus)
        if (!hasFocus) gamepadInputManager.reset()
    }

    override fun onPause() {
        previewScreen.pause()
        gamepadInputManager.reset()
        super.onPause()
    }
}
