package dev.retrobit.desktop.ui.apps

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.text.font.FontWeight
import dev.retrobit.desktop.data.WindowedLaunch
import dev.retrobit.desktop.ui.LauncherViewModel
import dev.retrobit.desktop.ui.components.Glyph
import dev.retrobit.desktop.ui.components.GlyphIcon
import dev.retrobit.desktop.ui.components.RetroButton
import dev.retrobit.desktop.ui.components.RetroText
import dev.retrobit.desktop.ui.components.dp
import dev.retrobit.desktop.ui.components.rememberAppIcon
import dev.retrobit.desktop.ui.theme.LocalPalette
import dev.retrobit.desktop.ui.wm.WinState

/**
 * Content of an "app window": the desktop-style wrapper around a third-party app.
 *
 * Android does not let one app draw another app's UI inside its own view, so the wrapper owns the
 * chrome (title bar, minimize / maximize / close, resize, snapping, workspace) and tells the system
 * where the app should live: on freeform-capable tablets the app opens in a real floating window at
 * exactly these bounds, and the buttons below move / re-open it there. On tablets without freeform
 * the app opens full screen and this window stays as its entry in the taskbar and window list.
 */
@Composable
fun AppWindowView(vm: LauncherViewModel, win: WinState) {
    val p = LocalPalette.current
    val app = win.app
    val freeform = WindowedLaunch.supportsFreeform(vm.appContext)
    val icon = app?.let { rememberAppIcon(it) }
    val bounds = win.rect
    val live = vm.appWindows.isRunning(win.id)

    Column(
        Modifier.fillMaxSize().background(p.paper).verticalScroll(rememberScrollState()).padding(dp(12f)),
        verticalArrangement = Arrangement.spacedBy(dp(10f)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dp(10f))) {
            Box(
                Modifier.size(dp(64f)).background(p.soft).border(dp(1.4f), p.ink),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) {
                    Image(icon, null, Modifier.size(dp(52f)), filterQuality = FilterQuality.None)
                } else {
                    GlyphIcon(Glyph.GRID, dp(34f))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(dp(3f))) {
                RetroText(app?.label ?: win.title, size = 14f, display = true, weight = FontWeight.Bold, maxLines = 1)
                RetroText(app?.pkg ?: "android", size = 10f, color = p.mid, maxLines = 1)
                RetroText(
                    if (vm.settings.floatingApps) "floating app window" else "full screen mode",
                    size = 10f, color = p.mid, maxLines = 1,
                )
            }
        }

        Box(Modifier.fillMaxWidth().height(dp(1.4f)).background(p.ink))

        // window geometry, kept in sync so the app can be moved onto it
        Column(verticalArrangement = Arrangement.spacedBy(dp(4f))) {
            RetroText("window bounds", size = 11f, weight = FontWeight.Bold)
            RetroText(
                "x ${bounds.left.toInt()}  y ${bounds.top.toInt()}  ${bounds.width.toInt()} x ${bounds.height.toInt()}",
                size = 11f, color = p.mid, maxLines = 1,
            )
            RetroText(
                if (freeform) {
                    "this tablet supports floating windows: the app is launched into this rectangle"
                } else {
                    "this tablet does not support floating windows, so the app opens full screen; " +
                        "this window keeps its place in the taskbar, window list and workspaces"
                },
                size = 10f, color = p.mid, maxLines = 4,
            )
        }

        if (freeform) {
            Row(horizontalArrangement = Arrangement.spacedBy(dp(8f))) {
                RetroButton(
                    label = "place here",
                    glyph = Glyph.GRID,
                    modifier = Modifier.weight(1f),
                    onClick = { vm.placeAppWindow(win) },
                )
                RetroButton(
                    label = "open full screen",
                    modifier = Modifier.weight(1f),
                    onClick = { app?.let { vm.launchFullScreen(it) } },
                )
            }
        } else {
            RetroButton(
                label = if (live) "app is running - bring it back" else "open app",
                glyph = Glyph.GRID,
                modifier = Modifier.fillMaxWidth(),
                onClick = { app?.let { vm.focusAppWindow(win) } },
            )
        }

        Box(Modifier.fillMaxWidth().height(dp(1.4f)).background(p.ink))
        Column(verticalArrangement = Arrangement.spacedBy(dp(5f))) {
            RetroText("window controls", size = 11f, weight = FontWeight.Bold)
            RetroText(
                "drag the title bar to move, drag any edge or corner to resize, double tap the title bar " +
                    "to maximize. The buttons at the top right minimize, maximize and close this window; " +
                    "the two-squares button floats it above the tiling layout.",
                size = 10f, color = p.mid, maxLines = 6,
            )
        }

        Spacer(Modifier.height(dp(4f)))
        RetroText("tip: " + WindowedLaunch.statusLine(vm.appContext), size = 9.5f, color = p.mid, maxLines = 3)
        RetroText(
            "enable floating windows: android developer options -> force activities to be resizable, " +
                "or adb shell settings put global enable_freeform_support 1",
            size = 9.5f, color = p.mid, maxLines = 4,
        )
    }
}
