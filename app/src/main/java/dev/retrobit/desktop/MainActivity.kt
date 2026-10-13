package dev.retrobit.desktop

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.retrobit.desktop.ui.LauncherViewModel
import dev.retrobit.desktop.ui.LocalViewModel
import dev.retrobit.desktop.ui.desktop.DesktopApp

class MainActivity : ComponentActivity() {

    private val vm: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        vm.requestDefaultLauncher = {
            runCatching {
                startActivity(
                    Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }

        setContent {
            CompositionLocalProvider(LocalViewModel provides vm) {
                Root(vm)
            }
        }
        vm.boot()
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
            modifier = if (settings.hideSystemBars) Modifier.fillMaxSize()
            else Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars),
        )
    }
}
