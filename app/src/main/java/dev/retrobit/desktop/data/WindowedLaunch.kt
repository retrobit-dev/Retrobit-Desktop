package dev.retrobit.desktop.data

import android.app.ActivityOptions
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle

/**
 * Floating ("windowed") app launching.
 *
 * Android has no public API that lets a normal app draw another app inside its own view — the only
 * way an app can genuinely float above the desktop is a **freeform window**, which the system
 * creates for us. We ask for one with [ActivityOptions.setLaunchBounds] plus
 * [ActivityOptions.setLaunchWindowingMode] semantics: on tablets that support freeform (or when the
 * user enabled it — developer options "force activities to be resizable" / `adb shell settings put
 * global enable_freeform_support 1`), the app opens in a real floating window at exactly the
 * rectangle our desktop window occupies. Where the feature is missing the system simply ignores the
 * bounds and opens the app full screen, which is why [supportsFreeform] is reported in Settings.
 */
object WindowedLaunch {

    /** True when the platform advertises freeform (floating) window support. */
    fun supportsFreeform(context: Context): Boolean =
        runCatching {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)
        }.getOrDefault(false)

    /** Android 12+ reports freeform through "PC mode"-style desktop features too. */
    fun supportsDesktopMode(context: Context): Boolean =
        runCatching {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                context.packageManager.hasSystemFeature("android.software.freeform_window_management")
        }.getOrDefault(false)

    /** Human readable status line for the Settings screen. */
    fun statusLine(context: Context): String = when {
        supportsFreeform(context) -> "this device supports floating windows: apps open in real windows"
        else -> "this device does not support floating windows: apps open full screen"
    }

    /**
     * Launch options requesting the given rectangle. Returns null when the platform has no freeform
     * support at all, so callers can skip the request and just launch normally.
     */
    fun activityOptionsFor(context: Context, bounds: Rect?): Bundle? {
        if (bounds == null || bounds.width() <= 0 || bounds.height() <= 0) return null
        if (!supportsFreeform(context)) return null
        return runCatching {
            ActivityOptions.makeBasic().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) setLaunchBounds(bounds)
            }.toBundle()
        }.getOrNull()
    }
}
