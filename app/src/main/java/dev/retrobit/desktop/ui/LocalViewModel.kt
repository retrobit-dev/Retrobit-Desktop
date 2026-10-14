package dev.retrobit.desktop.ui

import androidx.compose.runtime.staticCompositionLocalOf

val LocalViewModel = staticCompositionLocalOf<LauncherViewModel> { error("LauncherViewModel not provided") }
