package dev.retrobit.desktop.ui.apps

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.retrobit.desktop.BuildConfig
import dev.retrobit.desktop.ui.LauncherViewModel
import dev.retrobit.desktop.ui.components.Glyph
import dev.retrobit.desktop.ui.components.GlyphIcon
import dev.retrobit.desktop.ui.components.RetroText
import dev.retrobit.desktop.ui.components.dp
import dev.retrobit.desktop.ui.theme.LocalPalette

@Composable
fun AboutView(vm: LauncherViewModel) {
    val p = LocalPalette.current
    val logo = """
        ┌───────────────────────────────┐
        │   R E T R O B I T             │
        │   D E S K T O P   v1.0        │
        │                               │
        │   >_  e-paper launcher        │
        └───────────────────────────────┘
    """.trimIndent()

    Column(
        Modifier
            .fillMaxSize()
            .background(p.paper)
            .verticalScroll(rememberScrollState())
            .padding(dp(14f)),
        verticalArrangement = Arrangement.spacedBy(dp(10f)),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(dp(10f))) {
            GlyphIcon(Glyph.LOGO, dp(46f))
            RetroText(logo, size = 8.5f, maxLines = 8)
        }
        RetroText("RETROBIT DESKTOP", size = 20f, display = true, weight = FontWeight.Bold)
        RetroText("an Android launcher that dresses your tablet as an e-paper retro computer.", size = 11.5f, maxLines = 3)
        Box(Modifier.fillMaxWidth().height(dp(1.2f)).background(p.ink.copy(alpha = 0.4f)))
        kv("version", BuildConfig.VERSION_NAME)
        kv("build", BuildConfig.VERSION_CODE.toString())
        kv("android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        kv("device", "${Build.MANUFACTURER} ${Build.MODEL}")
        kv("desktop style", vm.settings.style.label + " · " + vm.settings.style.osName)
        kv("palette", vm.settings.palette.label)
        kv("wallpaper", vm.settings.wallpaper.label)
        kv("icon mode", vm.settings.iconMode.label)
        kv("windows open", vm.wm.windows.size.toString())
        kv("applications", vm.apps.size.toString())
        Box(Modifier.fillMaxWidth().height(dp(1.2f)).background(p.ink.copy(alpha = 0.4f)))
        RetroText("HINTS", size = 11f, weight = FontWeight.Bold)
        RetroText(
            "· tap an icon to launch an app\n" +
                "· tap & hold an icon for its menu (open in window, pin to dock, app info)\n" +
                "· drag an icon onto the dock or sidebar to pin it\n" +
                "· long-press the desktop for the system menu\n" +
                "\n" +
                "M-Pencil (treated as a mouse):\n" +
                "· 1 tap = left click · 2 taps = right click / context menu\n" +
                "· barrel button = right click · drag = move icons & windows\n" +
                "\n" +
                "Mouse: left click selects/opens, right click = context menu, wheel scrolls.\n" +
                "Keyboard: the on-screen keyboard appears when you tap a text field; the ⌨ button in the\n" +
                "panel/taskbar (or ctrl+alt+k) shows or hides it. External keyboards: ctrl+alt+t terminal ·\n" +
                "ctrl+alt+s settings · ctrl+alt+w close · ctrl+alt+m minimize all · ctrl+alt+d desktop menu ·\n" +
                "tab / ctrl+alt+arrows cycle windows · super = spotlight",
            size = 10.5f, color = p.mid, maxLines = 16,
        )
        RetroText("MIT-less personal project of retrobit-dev · fonts: VT323 & IBM Plex Mono (OFL)", size = 10f, color = p.mid, maxLines = 3)
    }
}

@Composable
private fun kv(key: String, value: String) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dp(8f))) {
        RetroText(key.padEnd(14), size = 11.5f, color = p.mid, modifier = Modifier.weight(1f))
        RetroText(value, size = 11.5f, modifier = Modifier.weight(1.4f))
    }
}
