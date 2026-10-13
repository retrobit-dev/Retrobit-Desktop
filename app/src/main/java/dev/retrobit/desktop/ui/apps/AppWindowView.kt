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
 * where the app should live: the frame's **content rectangle**, in screen pixels, is sent with
 * `ActivityOptions.setLaunchBounds`, so on a device that can float windows the app sits exactly
 * inside this box — below the title bar and inside the border. "Place here" sends the rectangle
 * again, "restart into frame" recreates the app's task (the only public way to move a running app).
 */
@Composable
fun AppWindowView(vm: LauncherViewModel, win: WinState) {
    val p = LocalPalette.current
    val app = win.app
    val bounds = win.rect
    val live = vm.appWindows.isRunning(win.id)
    val icon = app?.let { rememberAppIcon(it) }
    val freeform = WindowedLaunch.supportsFreeform(vm.appContext)
    val mayWork = WindowedLaunch.boundsMayBeHonoured(vm.appContext)
    val placement = vm.placementFor(win)
    val placed = vm.placedRect
    val density = vm.appContext.resources.displayMetrics.density

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
                    when {
                        !vm.settings.floatingApps -> "floating is off - the app opens full screen, this frame keeps its place in the taskbar"
                        live -> "placed in this frame - the app runs in its own floating window"
                        else -> "app window - tap place here to put the app inside the frame"
                    },
                    size = 10f, color = p.mid, maxLines = 2,
                )
            }
        }

        Box(Modifier.fillMaxWidth().height(dp(1.4f)).background(p.ink))

        // window geometry, kept in sync so the app can be moved onto it
        Column(verticalArrangement = Arrangement.spacedBy(dp(4f))) {
            RetroText("frame", size = 11f, weight = FontWeight.Bold)
            RetroText(
                "x ${bounds.left.toInt()}  y ${bounds.top.toInt()}  ${bounds.width.toInt()} x ${bounds.height.toInt()}" +
                    "  (desktop px)",
                size = 11f, color = p.mid, maxLines = 1,
            )
            RetroText(
                "safe area kept clear: ${vm.desktopInsets.left.toInt()}, ${vm.desktopInsets.top.toInt()} px  " +
                    "|  ui scale ${vm.settings.uiScale}  |  density ${"%.2f".format(density)}",
                size = 10f, color = p.mid, maxLines = 2,
            )
            RetroText(
                if (placement != null) {
                    "app rectangle sent to the system: ${placement.left}, ${placement.top}  " +
                        "${placement.width} x ${placement.height} px  (screen space, below the title bar)"
                } else {
                    "this frame is too small for an app window - resize it, then place again"
                },
                size = 10.5f, color = p.mid, maxLines = 3,
            )
            if (placed != null && placement != placed) {
                RetroText(
                    "the frame moved since the app was placed - place here again to match",
                    size = 10f, color = p.ink, maxLines = 2,
                )
            }
            RetroText(
                if (freeform) {
                    "this tablet supports floating windows: the app is launched into the rectangle above"
                } else if (mayWork) {
                    "no freeform flag, but this device can float windows (PiP capable): the rectangle is " +
                        "requested and is used when the system allows it"
                } else {
                    "this device does not report floating-window support, so the system may open the " +
                        "app full screen; this window keeps its place in the taskbar and workspaces"
                },
                size = 10f, color = p.mid, maxLines = 4,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(dp(8f))) {
            RetroButton(
                label = if (live) "place here again" else "place here",
                glyph = Glyph.GRID,
                modifier = Modifier.weight(1f),
                onClick = { vm.placeAppWindow(win) },
            )
            RetroButton(
                label = "restart into frame",
                modifier = Modifier.weight(1f),
                onClick = { vm.placeAppWindow(win, force = true) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(dp(8f))) {
            RetroButton(
                label = "bring it back",
                modifier = Modifier.weight(1f),
                onClick = { vm.focusAppWindow(win) },
            )
            RetroButton(
                label = "open full screen",
                modifier = Modifier.weight(1f),
                onClick = { app?.let { vm.launchFullScreen(it) } },
            )
        }
        RetroText(
            "android has no public api to move another app's task, so \"place here\" works when the app " +
                "is not running yet, and \"restart into frame\" recreates the app at the new rectangle. " +
                "Moving or resizing this frame re-places the app automatically while \"apps follow their " +
                "window\" is on (terminal: appframe follow on|off).",
            size = 10f, color = p.mid, maxLines = 6,
        )

        Box(Modifier.fillMaxWidth().height(dp(1.4f)).background(p.ink))
        Column(verticalArrangement = Arrangement.spacedBy(dp(5f))) {
            RetroText("window controls", size = 11f, weight = FontWeight.Bold)
            RetroText(
                "drag the title bar to move, drag any edge or corner to resize, double tap the title bar " +
                    "to maximize. The buttons at the top right minimize, maximize and close this window; " +
                    "the two-squares button floats it above the tiling layout.",
                size = 10f, color = p.mid, maxLines = 6,
            )
            RetroText(
                "maximize fills the whole desktop with this frame (and the app follows it); pressing " +
                    "maximize again restores the exact size and position the window had before.",
                size = 10f, color = p.mid, maxLines = 3,
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
