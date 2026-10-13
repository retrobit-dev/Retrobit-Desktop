package dev.retrobit.desktop.ui.apps

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.text.font.FontWeight
import dev.retrobit.desktop.model.AppEntry
import dev.retrobit.desktop.model.IconMode
import dev.retrobit.desktop.ui.LauncherViewModel
import dev.retrobit.desktop.ui.components.Glyph
import dev.retrobit.desktop.ui.components.GlyphIcon
import dev.retrobit.desktop.ui.components.RetroButton
import dev.retrobit.desktop.ui.components.RetroText
import dev.retrobit.desktop.ui.components.dp
import dev.retrobit.desktop.ui.components.rememberAppIcon
import dev.retrobit.desktop.ui.theme.LocalIconMode
import dev.retrobit.desktop.ui.theme.LocalPalette

/**
 * Live preview of the icon pipeline.
 *
 * Shows the user's own apps exactly as the desktop renders them (same palette, same mode, same shape,
 * same letter plate), then the same app in every icon mode and against the inverted paper, so a
 * change of palette, mode, shape or accent is visible right here instead of only out on the desktop.
 */
@Composable
fun AppIconPreview(vm: LauncherViewModel) {
    val p = LocalPalette.current
    val s = vm.settings
    val apps = remember(vm.apps) {
        val pinned = vm.appsByPackages(s.pinnedDesktop + s.pinnedDock)
        (pinned + vm.apps).distinctBy { it.key }.take(6)
    }

    Column(verticalArrangement = Arrangement.spacedBy(dp(7f))) {
        Row(
            Modifier.fillMaxWidth().background(p.soft).border(dp(1.4f), p.ink).padding(dp(8f)),
            horizontalArrangement = Arrangement.spacedBy(dp(8f)),
        ) {
            if (apps.isEmpty()) {
                RetroText("no apps found yet", size = 10.5f, color = p.mid, maxLines = 1)
            }
            apps.forEach { app -> PreviewTile(app, size = 40f) }
        }

        val sample = apps.firstOrNull()
        if (sample != null) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(dp(10f)),
            ) {
                IconMode.entries.forEach { mode ->
                    CompositionLocalProvider(LocalIconMode provides mode) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(dp(3f)),
                        ) {
                            Box(
                                Modifier
                                    .size(dp(46f))
                                    .border(
                                        if (mode == s.iconMode) dp(2f) else dp(1f),
                                        if (mode == s.iconMode) p.ink else p.mid,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                PreviewTile(sample, size = 34f, showName = false)
                            }
                            RetroText(mode.label, size = 9.5f, maxLines = 1)
                        }
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(dp(3f)),
                ) {
                    Box(
                        Modifier.size(dp(46f)).background(p.ink),
                        contentAlignment = Alignment.Center,
                    ) {
                        PreviewTile(sample, size = 34f, showName = false, inverted = true)
                    }
                    RetroText("invert", size = 9.5f, maxLines = 1)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(dp(6f))) {
            RetroButton(
                label = "re-ink icons now",
                glyph = Glyph.LOGO,
                modifier = Modifier.weight(1f),
                onClick = { vm.reinkIcons() },
            )
            IconMode.entries.forEach { mode ->
                RetroButton(
                    label = mode.label.lowercase(),
                    active = s.iconMode == mode,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        s.iconMode = mode
                        vm.reinkIcons()
                    },
                )
            }
            RetroButton(
                label = if (s.iconAccent) "accent on" else "accent off",
                active = s.iconAccent,
                modifier = Modifier.weight(1f),
                onClick = {
                    s.iconAccent = !s.iconAccent
                    vm.reinkIcons()
                },
            )
        }
        RetroText(
            "If a desktop icon ever looks stale, press re-ink icons now: it drops the whole bitmap " +
                "cache and every icon is rendered again from the source app.",
            size = 10f, color = p.mid, maxLines = 3,
        )
    }
}

@Composable
private fun PreviewTile(app: AppEntry, size: Float, showName: Boolean = true, inverted: Boolean = false) {
    val label = app.label
    val icon = rememberAppIcon(app, inverted = inverted)
    Column(
        Modifier.width(dp(size + 14f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dp(3f)),
    ) {
        Box(Modifier.size(dp(size)), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Image(icon, label, Modifier.size(dp(size - 2f)), filterQuality = FilterQuality.None)
            } else {
                GlyphIcon(Glyph.FOLDER, dp(size * 0.75f))
            }
        }
        if (showName) {
            RetroText(label, size = 9f, maxLines = 2, weight = FontWeight.Bold)
        }
    }
}
