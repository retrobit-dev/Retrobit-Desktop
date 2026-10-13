package dev.retrobit.desktop.ui.boot

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.retrobit.desktop.ui.LauncherViewModel
import dev.retrobit.desktop.ui.components.Glyph
import dev.retrobit.desktop.ui.components.GlyphIcon
import dev.retrobit.desktop.ui.components.RetroProgress
import dev.retrobit.desktop.ui.components.RetroText
import dev.retrobit.desktop.ui.components.dp

@Composable
fun BootScreen(vm: LauncherViewModel, onSkip: () -> Unit) {
    val p = dev.retrobit.desktop.ui.theme.LocalPalette.current
    val pulse by rememberInfiniteTransition(label = "boot").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "cursor",
    )
    val progress = (vm.bootLines.size / 9f).coerceIn(0f, 1f)

    Box(
        Modifier
            .fillMaxSize()
            .background(p.paper)
            .padding(dp(28f)),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(dp(14f))) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dp(14f))) {
                GlyphIcon(Glyph.LOGO, dp(52f))
                Column {
                    RetroText("RETROBIT DESKTOP", size = 30f, display = true, weight = FontWeight.Bold)
                    RetroText("e-paper launcher · v1.0", size = 12f, color = p.mid)
                }
            }
            Box(Modifier.fillMaxWidth().height(dp(2f)).background(p.ink))
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(dp(4f)),
            ) {
                vm.bootLines.forEach { line ->
                    RetroText(line, size = 13f, maxLines = 1)
                }
                Spacer(Modifier.height(dp(4f)))
                RetroText(if (pulse > 0.5f) "_" else " ", size = 13f)
            }
            RetroProgress(progress, cells = 32, thickness = dp(12f))
            RetroText("tap anywhere to skip", size = 10.5f, color = p.mid)
        }
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSkip,
                ),
        )
    }
}
