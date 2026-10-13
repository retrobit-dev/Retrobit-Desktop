package dev.retrobit.desktop

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.retrobit.desktop.ui.LauncherViewModel
import dev.retrobit.desktop.ui.LocalViewModel
import dev.retrobit.desktop.ui.desktop.DesktopApp
import dev.retrobit.desktop.ui.input.KeyboardRequester
import dev.retrobit.desktop.ui.input.LocalKeyboardRequester

class MainActivity : ComponentActivity() {

    private val vm: LauncherViewModel by viewModels()
    private val keyboard = KeyboardRequester()

    /** Everything the rails and the quick panel ask for arrives on this private bus. */
    private val bus = dev.retrobit.desktop.shell.PanelSwitch { action -> vm.onPanelAction(action) }
    private var busRegistered = false

    /** Display-cutout insets (punch-hole camera) as [left, top, right, bottom] in px. */
    private val cutout = androidx.compose.runtime.mutableStateOf(IntArray(0))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        vm.toggleKeyboard = {
            val imm = getSystemService(InputMethodManager::class.java)
            if (keyboard.visible) {
                imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
            } else {
                val field = keyboard.lastFocus
                if (field != null) {
                    // refocus the last text field, then let the IME attach to it
                    runCatching { field.requestFocus() }
                    window.decorView.postDelayed(
                        { runCatching { imm.showSoftInput(window.decorView, InputMethodManager.SHOW_IMPLICIT) } },
                        90,
                    )
                } else {
                    vm.shell.print("keyboard: tap a text field first (terminal input, notes, search box)")
                }
            }
        }

        vm.requestDefaultLauncher = {
            runCatching {
                startActivity(
                    Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }

        // the punch-hole camera: remember the display cutout so the shells can steer around it
        window.decorView.setOnApplyWindowInsetsListener { _, insets: android.view.WindowInsets ->
            val cut = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                insets.displayCutout
            } else {
                null
            }
            cutout.value = if (cut == null) {
                IntArray(0)
            } else {
                intArrayOf(cut.safeInsetLeft, cut.safeInsetTop, cut.safeInsetRight, cut.safeInsetBottom)
            }
            insets
        }

        setContent {
            CompositionLocalProvider(
                LocalViewModel provides vm,
                LocalKeyboardRequester provides keyboard,
                dev.retrobit.desktop.ui.theme.LocalCutoutInsets provides cutout.value,
            ) {
                Root(vm)
            }
        }
        vm.boot()
    }

    override fun onResume() {
        super.onResume()
        vm.setAppForeground(true)
        // the user may have installed or removed apps while we were in the background
        vm.refreshApps()
    }

    override fun onPause() {
        super.onPause()
        // stop the clock / battery timer while another app is in front
        vm.setAppForeground(false)
    }

    override fun onStart() {
        super.onStart()
        if (!busRegistered) {
            dev.retrobit.desktop.shell.PanelSwitch.register(this, bus)
            busRegistered = true
        }
        // the desktop is on screen: its own frames are visible, so the overlay chrome steps aside
        vm.onLauncherVisible()
    }

    override fun onStop() {
        if (busRegistered) {
            dev.retrobit.desktop.shell.PanelSwitch.unregister(this, bus)
            busRegistered = false
        }
        super.onStop()
        // An app owns the screen now. If the window manager could not float it, the chrome is what
        // keeps the frame visible around it. (A floating app only pauses us, so no chrome then.)
        vm.onLauncherHidden()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@Composable
private fun Root(vm: LauncherViewModel) {
    val view = LocalView.current
    val settings = vm.settings
    val keyboard = LocalKeyboardRequester.current
    val density = LocalDensity.current
    // The internal keyboard is allowed to appear whenever it is needed; the desktop simply slides
    // up by the keyboard height so the focused field and the tray button stay reachable.
    val imeHeight = WindowInsets.ime.getBottom(density)

    LaunchedEffect(imeHeight > 0) { keyboard.visible = imeHeight > 0 }

    // the quick panel owns the screen brightness while the launcher is in front
    LaunchedEffect(vm.brightnessRequest, settings.brightness) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val wanted = settings.brightness
        if (wanted > 0f) {
            window.attributes = window.attributes.apply { screenBrightness = wanted.coerceIn(0.05f, 1f) }
        } else {
            // -1 hands the brightness back to the tablet; the value the window had is reported back so
            // the panel's B- / B+ can start from something sensible
            vm.noteBrightness(if (window.attributes.screenBrightness > 0f) window.attributes.screenBrightness else 0.5f)
            window.attributes = window.attributes.apply { screenBrightness = -1f }
        }
    }

    // rotation lock from the quick panel (the manifest default is fullUser)
    LaunchedEffect(settings.rotateLocked) {
        requestedOrientation = if (settings.rotateLocked) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(settings.hideSystemBars) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        if (settings.hideSystemBars) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    BackHandler(enabled = true) {
        when {
            vm.panelOpen -> vm.closePanel()
            vm.menu != null -> vm.menu = null
            vm.spotlight -> vm.spotlight = false
            vm.launchpad -> vm.launchpad = false
            vm.startMenu -> vm.startMenu = false
            vm.wm.windows.any { !it.minimized } -> vm.wm.minimizeAll()
            else -> Unit
        }
    }

    Box(Modifier.fillMaxSize()) {
        DesktopApp(
            vm,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (settings.hideSystemBars) Modifier
                    else Modifier.windowInsetsPadding(WindowInsets.systemBars)
                )
                .offset { IntOffset(0, -imeHeight) },
        )
    }
}
