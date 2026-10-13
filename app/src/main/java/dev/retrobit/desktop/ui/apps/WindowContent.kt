package dev.retrobit.desktop.ui.apps

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.retrobit.desktop.ui.LocalViewModel
import dev.retrobit.desktop.ui.wm.WinState

@Composable
fun WindowContent(win: WinState, wm: dev.retrobit.desktop.ui.wm.WindowManager) {
    val vm = LocalViewModel.current
    when (win.kind) {
        dev.retrobit.desktop.model.WinKind.APPS -> AppsView(vm, win)
        dev.retrobit.desktop.model.WinKind.TERMINAL -> TerminalView(vm)
        dev.retrobit.desktop.model.WinKind.SETTINGS -> SettingsView(vm)
        dev.retrobit.desktop.model.WinKind.SYSMON -> SystemMonitorView(vm)
        dev.retrobit.desktop.model.WinKind.NOTES -> NotesView(vm)
        dev.retrobit.desktop.model.WinKind.ABOUT -> AboutView(vm)
    }
}
