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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
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

@Composable
fun TerminalView(vm: LauncherViewModel) {
    val p = LocalPalette.current
    val shell = vm.shell
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(shell.lines.size) {
        if (shell.lines.isNotEmpty()) listState.scrollToItem(shell.lines.size - 1)
    }

    Column(Modifier.fillMaxSize().background(p.paper)) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = dp(10f), vertical = dp(8f))) {
            SelectionContainer {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(shell.lines) { line ->
                        val color = when (line.kind) {
                            TerminalShell.Kind.ERR -> p.ink.copy(alpha = 0.95f)
                            TerminalShell.Kind.DIM -> p.mid
                            TerminalShell.Kind.HEAD -> p.ink
                            else -> p.ink
                        }
                        RetroText(
                            text = if (line.kind == TerminalShell.Kind.IN) line.text else line.text.ifEmpty { " " },
                            color = color,
                            size = 13f,
                            weight = if (line.kind == TerminalShell.Kind.HEAD) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth().padding(vertical = dp(0.5f)),
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(dp(1.4f)).background(p.edge))
        Row(
            Modifier.fillMaxWidth().padding(dp(8f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dp(8f)),
        ) {
            RetroText(shell.prompt(), size = 13f, weight = FontWeight.Bold)
            Box(Modifier.weight(1f)) {
                RetroTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = "type a command…",
                    onSubmit = {
                        shell.submit(input)
                        input = ""
                    },
                )
            }
            QuickKeys { key ->
                when (key) {
                    Quick.HELP -> shell.submit("help")
                    Quick.PREV -> input = shell.historyPrev(input)
                    Quick.NEXT -> input = shell.historyNext()
                    Quick.CLEAR -> shell.clear()
                }
            }
        }
    }
}

/**
 * The terminal's quick keys, drawn as icons only: help (question mark), previous / next command
 * (arrows) and clear (cross). Four small boxes instead of four word buttons, so the input line keeps
 * its width; the commands themselves are still listed by `help`.
 */
private enum class Quick(val glyph: dev.retrobit.desktop.ui.components.Glyph) {
    HELP(dev.retrobit.desktop.ui.components.Glyph.HELP),
    PREV(dev.retrobit.desktop.ui.components.Glyph.UP),
    NEXT(dev.retrobit.desktop.ui.components.Glyph.DOWN),
    CLEAR(dev.retrobit.desktop.ui.components.Glyph.CLOSE),
}

@Composable
private fun QuickKeys(onKey: (Quick) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(dp(5f)),
    ) {
        Quick.entries.forEach { key ->
            dev.retrobit.desktop.ui.components.RetroIconButton(
                glyph = key.glyph,
                boxSize = dp(30f),
                onClick = { onKey(key) },
            )
        }

    }
}
