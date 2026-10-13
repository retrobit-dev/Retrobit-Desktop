package dev.retrobit.desktop.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.retrobit.desktop.ui.LauncherViewModel
import dev.retrobit.desktop.ui.components.RetroText
import dev.retrobit.desktop.ui.components.RetroTextField
import dev.retrobit.desktop.ui.components.dp
import dev.retrobit.desktop.ui.theme.LocalPalette
import kotlinx.coroutines.delay

@Composable
fun NotesView(vm: LauncherViewModel) {
    val p = LocalPalette.current
    var text by remember { mutableStateOf(vm.settings.notes) }
    var saved by remember { mutableStateOf(true) }

    LaunchedEffect(text) {
        saved = false
        delay(500)
        vm.settings.notes = text
        saved = true
    }

    Column(Modifier.fillMaxSize().background(p.paper)) {
        Row(
            Modifier.fillMaxWidth().background(p.soft).padding(dp(8f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dp(8f)),
        ) {
            RetroText("notes.txt", size = 12f, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
            RetroText(if (saved) "saved" else "saving…", size = 10.5f, color = p.mid)
            RetroText("${text.lines().size} L · ${text.length} C", size = 10.5f, color = p.mid)
        }
        Box(Modifier.fillMaxWidth().height(dp(1.4f)).background(p.ink))
        Box(Modifier.fillMaxSize().padding(dp(10f))) {
            RetroTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = "jot something down…",
                singleLine = false,
                fill = p.paper,
                textSize = 13f,
            )
        }
    }
}
