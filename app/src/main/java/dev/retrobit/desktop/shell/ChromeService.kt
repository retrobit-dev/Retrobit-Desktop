package dev.retrobit.desktop.shell

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.retrobit.desktop.MainActivity
import dev.retrobit.desktop.R
import dev.retrobit.desktop.data.SystemInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Draws the **cyberdeck chrome** — the desktop frame, the title rail and the dock — on top of apps
 * that the system opened full screen.
 *
 * Android gives a launcher only one way to put another app *inside* a frame: a freeform window,
 * created by the system from the rectangle the launcher sends with `ActivityOptions.setLaunchBounds`.
 * Tablets without freeform windowing (most of them, including EMUI / HarmonyOS) ignore that
 * rectangle: the app covers the desktop and the frame behind it is never seen. This service keeps the
 * *look* of the desktop in that case by drawing the frame over the app. It needs the "display over
 * other apps" permission, runs as a foreground service so a stopped launcher cannot take the frame
 * down with it, and talks to the launcher view model through [CyberdeckChrome].
 *
 * The rails are deliberately thin, and the middle of the screen has no overlay window at all, so the
 * app underneath keeps every one of its own gestures; the dock also stays clear of the navigation
 * bar (gesture pill) so the system's own "go home" swipe keeps working.
 */
class ChromeService : Service() {

    companion object {
        private const val CHANNEL = "retrobit_chrome"
        private const val NOTIF_ID = 4711

        private const val ACTION_START = "dev.retrobit.desktop.chrome.START"
        private const val ACTION_SHOW = "dev.retrobit.desktop.chrome.SHOW"
        private const val ACTION_HIDE = "dev.retrobit.desktop.chrome.HIDE"
        private const val ACTION_PREVIEW = "dev.retrobit.desktop.chrome.PREVIEW"
        private const val EXTRA_SECONDS = "seconds"

        /** True while the service is alive (the Settings screen reports it). */
        @Volatile
        var running: Boolean = false
            private set

        /** True while the rails are actually on screen. */
        @Volatile
        var visible: Boolean = false
            private set

        /** Starts the chrome service and keeps it warm; nothing is drawn until [show]. */
        fun start(ctx: Context) = send(ctx, ACTION_START, foreground = true)

        /** Shows the chrome. Safe from the background because the service is already up. */
        fun show(ctx: Context) {
            if (running) send(ctx, ACTION_SHOW, foreground = false) else send(ctx, ACTION_SHOW, foreground = true)
        }

        fun hide(ctx: Context) {
            if (running) send(ctx, ACTION_HIDE, foreground = false)
        }

        fun stop(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, ChromeService::class.java)) }
        }

        /** Shows the chrome for a few seconds while the launcher is still in front (a preview). */
        fun preview(ctx: Context, seconds: Int = 8) = send(ctx, ACTION_PREVIEW, foreground = true, seconds = seconds)

        private fun send(ctx: Context, action: String, foreground: Boolean, seconds: Int = 0) {
            val intent = Intent(ctx, ChromeService::class.java).setAction(action)
            if (seconds > 0) intent.putExtra(EXTRA_SECONDS, seconds)
            runCatching {
                if (foreground) ContextCompat.startForegroundService(ctx, intent) else ctx.startService(intent)
            }
        }
    }

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var border: BorderView? = null
    private var top: RailView? = null
    private var dock: RailView? = null
    private var statusInset = 0
    private var navInset = 0
    private var sizeW = 0
    private var sizeH = 0
    private var previewLeft = 0
    private var ticks = 0
    private var battery = ""
    private var notchLeft = 0
    private var railsShown = true
    private var railsHiddenAt = 0L
    private var lastTitle = ""
    private val clock = SimpleDateFormat("HH:mm", Locale.US)

    /** App icons for the dock, loaded once per package (64 px, printed in grayscale). */
    private val iconCache = HashMap<String, android.graphics.Bitmap?>()

    private val tick = object : Runnable {
        override fun run() {
            // the launcher view model refreshes the shared state (title, dock, palette, desktops)
            runCatching { CyberdeckChrome.onRefresh?.invoke() }
            updateBattery()
            applyState()
            autoHideTick()
            if (previewLeft > 0) {
                previewLeft--
                if (previewLeft == 0) hide()
            }
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WindowManager::class.java)
        running = true
        measure()
        handler.postDelayed(tick, 500L)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        remove()
        running = false
        visible = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> hide()
            ACTION_PREVIEW -> {
                promote()
                previewLeft = intent.getIntExtra(EXTRA_SECONDS, 8).coerceIn(2, 60)
                show()
            }
            ACTION_SHOW -> {
                promote()
                show()
            }
            else -> promote()
        }
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // rotation / split screen / a fold: the rails have to be measured again
        val wasVisible = visible
        remove()
        measure()
        if (wasVisible) show()
    }

    // ── foreground ──────────────────────────────────────────────────────────────
    private fun promote() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL) == null) {
            val channel = NotificationChannel(CHANNEL, "cyberdeck chrome", NotificationManager.IMPORTANCE_MIN)
                .apply { description = "the desktop frame drawn over full-screen apps" }
            runCatching { nm.createNotificationChannel(channel) }
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("cyberdeck chrome")
            .setContentText("the desktop frame is drawn over apps that open full screen")
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(open)
            .build()
        runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                },
            )
        }
    }

    // ── the windows ─────────────────────────────────────────────────────────────
    private fun canDraw(): Boolean = runCatching { Settings.canDrawOverlays(this) }.getOrDefault(false)

    private fun measure() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = wm.currentWindowMetrics
            sizeW = metrics.bounds.width()
            sizeH = metrics.bounds.height()
            val insets = metrics.windowInsets
            statusInset = insets.getInsetsIgnoringVisibility(
                WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout(),
            ).top
            navInset = insets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars()).bottom
            // the punch-hole camera: the title of the rail has to start after it
            notchLeft = insets.getInsetsIgnoringVisibility(WindowInsets.Type.displayCutout()).left
        } else {
            val dm = resources.displayMetrics
            sizeW = dm.widthPixels
            sizeH = dm.heightPixels
            statusInset = (24f * dm.density).toInt()
            navInset = (24f * dm.density).toInt()
            notchLeft = 0
        }
    }

    private fun show() {
        if (visible) {
            applyState()
            return
        }
        if (!canDraw()) return
        measure()
        // set before the views are created: their height is read while they are added
        railsShown = true
        runCatching {
            val ink = CyberdeckChrome.state
            val b = BorderView(this).apply { state = ink }
            val p = params(sizeW, sizeH, Gravity.TOP or Gravity.START)
            p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            wm.addView(b, p)
            border = b

            if (ink.topRail) {
                val t = RailView(this, topRail = true).apply {
                    inset = statusInset
                    notchLeft = notchLeft
                    icons = { pkg -> appIcon(pkg) }
                    onAction = { action -> onAction(action) }
                    onInteract = { keepAwake() }
                }
                wm.addView(t, params(sizeW, barsHeight(t), Gravity.TOP or Gravity.START))
                top = t
            }
            if (ink.bottomDock) {
                val d = RailView(this, topRail = false).apply {
                    inset = navInset
                    notchLeft = 0
                    icons = { pkg -> appIcon(pkg) }
                    onAction = { action -> onAction(action) }
                    onInteract = { keepAwake() }
                }
                wm.addView(d, params(sizeW, barsHeight(d), Gravity.BOTTOM or Gravity.START))
                dock = d
            }
            visible = true
            railsHiddenAt = 0L
            lastTitle = CyberdeckChrome.state.title
            updateBattery(force = true)
            applyState()
        }.onFailure {
            remove()
        }
    }

    private fun hide() {
        remove()
        visible = false
    }

    private fun remove() {
        hideAt = 0L
        border?.let { runCatching { wm.removeView(it) } }
        top?.let { runCatching { wm.removeView(it) } }
        dock?.let { runCatching { wm.removeView(it) } }
        border = null
        top = null
        dock = null
        visible = false
    }

    /** A full-width overlay strip that never takes focus and never animates. */
    private fun params(w: Int, h: Int, where: Int): WindowManager.LayoutParams {
        val p = WindowManager.LayoutParams(
            w,
            h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        )
        p.gravity = where
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        return p
    }

    private fun applyState() {
        val state = CyberdeckChrome.state
        if (visible && (border == null)) return
        // a rail the user just turned off (or on) has to follow while the chrome is up
        if (visible && (state.topRail != (top != null) || state.bottomDock != (dock != null))) {
            val wasShown = railsShown
            remove()
            show()
            if (!wasShown) setRailsShown(false)
            return
        }
        // a new app came to the front: show the title rail for a moment so the user sees its name
        if (visible && state.title != lastTitle) {
            lastTitle = state.title
            keepAwake()
        }
        val clockText = clockText()
        border?.let {
            it.state = state
            it.invalidate()
        }
        listOfNotNull(top, dock).forEach {
            it.state = state
            it.clock = clockText
            it.battery = battery
            it.net = state.net
            it.invalidate()
        }
    }

    // ── auto-hide ───────────────────────────────────────────────────────────────
    /** Height of a rail window: the full rail, or the handle it shrinks to when hidden. */
    private fun barsHeight(view: RailView): Int = if (railsShown) view.totalHeight() else view.handleHeight()

    /** Puts the rails back (and starts their countdown again) — used by the handle and by presses. */
    private fun keepAwake() {
        val secs = CyberdeckChrome.state.autoHideSecs.coerceIn(3, 60)
        railsHiddenAt = 0L
        if (!railsShown) setRailsShown(true)
        hideAt = System.currentTimeMillis() + secs * 1000L
    }

    private var hideAt = 0L

    private fun setRailsShown(shown: Boolean) {
        if (shown == railsShown) return
        railsShown = shown
        listOfNotNull(top, dock).forEach { view ->
            view.handlesOnly = !shown
            val p = view.layoutParams as? WindowManager.LayoutParams ?: return@forEach
            p.height = barsHeight(view)
            runCatching { wm.updateViewLayout(view, p) }
            view.invalidate()
        }
    }

    /** Slides the rails away after their countdown; the frame itself always stays. */
    private fun autoHideTick() {
        val state = CyberdeckChrome.state
        val wanted = state.autoHide
        val now = System.currentTimeMillis()
        if (!wanted) {
            hideAt = 0L
            if (!railsShown) setRailsShown(true)
            return
        }
        if (!railsShown) {
            railsHiddenAt = now
            return
        }
        if (hideAt == 0L) hideAt = now + state.autoHideSecs.coerceIn(3, 60) * 1000L
        // never hide while the user is watching a preview or has just brought us back
        if (previewLeft > 0) {
            hideAt = now + state.autoHideSecs.coerceIn(3, 60) * 1000L
            return
        }
        if (now >= hideAt) {
            hideAt = 0L
            setRailsShown(false)
        }
    }

    /** One 64 px icon per package, printed in grayscale by the dock. */
    private fun appIcon(pkg: String?): android.graphics.Bitmap? {
        if (pkg == null || pkg.isBlank()) return null
        if (iconCache.containsKey(pkg)) return iconCache[pkg]
        val bitmap = runCatching {
            val drawable = packageManager.getApplicationIcon(pkg)
            val size = (64 * resources.displayMetrics.density / 3f).toInt().coerceIn(48, 128)
            val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            bmp
        }.getOrNull()
        iconCache[pkg] = bitmap
        return bitmap
    }

    // ── the readouts ────────────────────────────────────────────────────────────
    private fun clockText(): String = runCatching { clock.format(Date()) }.getOrDefault("")

    private fun updateBattery(force: Boolean = false) {
        ticks++
        if (!force && ticks % 15 != 0) return
        val snapshot = runCatching { SystemInfo.snapshot(this) }.getOrNull() ?: return
        val percent = snapshot.battery
        battery = when {
            percent < 0 -> ""
            snapshot.charging -> "bat $percent%+"
            else -> "bat $percent%"
        }
    }

    // ── what the rails do ───────────────────────────────────────────────────────
    private fun onAction(action: String) {
        hideAt = 0L
        when {
            action == "home" -> {
                val home = CyberdeckChrome.onHome
                hide()
                if (home != null) {
                    runCatching { home() }
                } else {
                    runCatching {
                        startActivity(
                            Intent(Intent.ACTION_MAIN)
                                .addCategory(Intent.CATEGORY_HOME)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
            }
            action == "hide" -> {
                val off = CyberdeckChrome.onHideChrome
                hide()
                if (off != null) {
                    runCatching { off() }
                } else {
                    runCatching {
                        getSharedPreferences("retrobit", MODE_PRIVATE).edit()
                            .putBoolean("cyberdeckChrome", false).apply()
                    }
                    stopSelf()
                }
            }
            action == "fit" -> {
                val refit = CyberdeckChrome.onRefit
                hide()
                if (refit != null) runCatching { refit() }
            }
            action.startsWith("win:") -> {
                val id = action.removePrefix("win:").toIntOrNull()
                val open = CyberdeckChrome.onOpenWindow
                hide()
                if (id != null && open != null) runCatching { open(id) }
            }
            action.startsWith("app:") -> {
                val pkg = action.removePrefix("app:")
                val open = CyberdeckChrome.onOpenPackage
                hide()
                if (open != null) {
                    runCatching { open(pkg) }
                } else {
                    runCatching {
                        packageManager.getLaunchIntentForPackage(pkg)?.let {
                            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(it)
                        }
                    }
                }
            }
        }
    }
}
