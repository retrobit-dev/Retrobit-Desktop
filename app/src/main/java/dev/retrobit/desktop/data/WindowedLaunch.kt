package dev.retrobit.desktop.data

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import dev.retrobit.desktop.ui.wm.AppWindowMath
import dev.retrobit.desktop.ui.wm.Placement

/**
 * Floating ("windowed") app launching.
 *
 * Android has no public API that lets a normal app draw another app inside its own view, so the only
 * way an app can genuinely sit inside a desktop window frame is a **freeform window**: the system
 * creates the window, we only get to say where.
 *
 * How we ask ([activityOptionsFor]): [ActivityOptions.setLaunchBounds] with the rectangle of the
 * frame's *content area*, in **screen pixels** — the position Must include the safe-area padding and
 * must start below the title bar, otherwise the app lands slightly off and covers the frame's own
 * chrome (which is exactly what "Place here" used to do wrong).
 *
 * Notes straight from the framework: `setLaunchBounds` is honoured on devices that have
 * `FEATURE_FREEFORM_WINDOW_MANAGEMENT` or `FEATURE_PICTURE_IN_PICTURE` enabled and is *ignored*
 * elsewhere, so the request is always sent — it is harmless on devices that cannot honour it, and
 * some OEM desktop modes honour it without advertising the feature flag. There is no public API to
 * move an already-running task, so re-placing a running app is done by relaunching it with the fresh
 * bounds ([AppRepository.launch] with `force = true`).
 */
object WindowedLaunch {

    /** True when the platform advertises freeform (floating) window support. */
    fun supportsFreeform(context: Context): Boolean =
        runCatching {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)
        }.getOrDefault(false)

    /** Android also honours launch bounds on picture-in-picture capable devices. */
    fun supportsPictureInPicture(context: Context): Boolean =
        runCatching {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        }.getOrDefault(false)

    /** Best guess before launching: the device may actually place windows for us. */
    fun boundsMayBeHonoured(context: Context): Boolean =
        supportsFreeform(context) || supportsPictureInPicture(context)

    /**
     * `ActivityOptions.setLaunchWindowingMode` is hidden, yet it is the only way to *ask* for a
     * freeform window instead of hoping the system turns a rectangle into one. It is looked up once,
     * through reflection, and simply stays null on builds that refuse access to it (hidden-API
     * enforcement on release builds) — nothing here is required for the launcher to work.
     */
    private val windowingMode: java.lang.reflect.Method? by lazy {
        runCatching {
            ActivityOptions::class.java
                .getDeclaredMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                .apply { isAccessible = true }
        }.getOrNull()
    }

    /** `WINDOWING_MODE_FREEFORM` from the framework: a real, movable, resizable system window. */
    private const val WINDOWING_MODE_FREEFORM = 5

    /** True when this build lets us ask for the freeform windowing mode explicitly. */
    fun canAskForFreeform(): Boolean = windowingMode != null

    /** Human readable status line for the Settings screen. */
    fun statusLine(context: Context): String = when {
        supportsFreeform(context) ->
            "floating windows are supported: apps are placed in exactly the frame rectangle"
        supportsPictureInPicture(context) ->
            "no freeform flag, but this device supports floating/PiP windows: the frame rectangle is " +
                "requested anyway and is used when the system allows it"
        else ->
            "this device does not report floating-window support: the rectangle is still requested, " +
                "but the system may open the app full screen"
    } + if (canAskForFreeform()) {
        " (this build also answers the hidden \"launch as a freeform window\" call, which is asked " +
            "for first)"
    } else {
        ""
    }

    /**
     * Launch options requesting [placement]. Always built when we have a rectangle: the system ignores
     * it when it cannot place windows, and uses it when it can.
     */
    fun activityOptionsFor(placement: Placement?): Bundle? {
        if (placement == null || !placement.isUsable) return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        return runCatching {
            val options = ActivityOptions.makeBasic().setLaunchBounds(placement.toRect())
            // ask for a freeform window explicitly: with a plain rectangle the system is free to
            // decide that the app belongs in the fullscreen stack instead (which is what most
            // tablets do), while the windowing mode is what actually makes it float
            windowingMode?.let { mode -> runCatching { mode.invoke(options, WINDOWING_MODE_FREEFORM) } }
            options.toBundle()
        }.getOrNull()
    }

    /**
     * True when [placement] is worth sending: a real rectangle smaller than the screen.
     */
    fun worthPlacing(placement: Placement?, screenW: Int, screenH: Int): Boolean =
        AppWindowMath.isWindowed(placement, screenW, screenH)

    /** The plain launch intent of [pkg], used for the "restart into this frame" path. */
    fun launchIntent(context: Context, pkg: String): Intent? =
        runCatching { context.packageManager.getLaunchIntentForPackage(pkg) }.getOrNull()
}
