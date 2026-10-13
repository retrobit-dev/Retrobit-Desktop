package dev.retrobit.desktop.shell

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager

/**
 * The overlay window of the quick panel ([PanelView]).
 *
 * A service of its own, like [ChromeService], so the panel can be opened while another app owns the
 * screen and can outlive a paused launcher. It takes input focus while it is up (that is what makes
 * the brightness groove draggable and the Esc key work), and gives it back when it closes.
 */
class PanelService : Service() {

    companion object {
        private const val ACTION_SHOW = "dev.retrobit.desktop.panel.SHOW"
        private const val ACTION_HIDE = "dev.retrobit.desktop.panel.HIDE"
        private const val ACTION_SYNC = "dev.retrobit.desktop.panel.SYNC"

        @Volatile
        var running: Boolean = false
            private set

        fun show(ctx: Context) {
            // a plain background service: while the chrome is up the app already holds a foreground
            // service, which is what lets this one start from the background at all
            val intent = Intent(ctx, PanelService::class.java).setAction(ACTION_SHOW)
            runCatching { ctx.startService(intent) }
        }

        fun hide(ctx: Context) {
            runCatching { ctx.startService(Intent(ctx, PanelService::class.java).setAction(ACTION_HIDE)) }
        }

        /** Pushes fresh state into a panel that is already up. */
        fun sync(ctx: Context) {
            runCatching { ctx.startService(Intent(ctx, PanelService::class.java).setAction(ACTION_SYNC)) }
        }
    }

    private lateinit var wm: WindowManager
    private var view: PanelView? = null
    private var sizeW = 0
    private var sizeH = 0

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WindowManager::class.java)
        running = true
        measure()
    }

    override fun onDestroy() {
        dismiss()
        running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> dismiss()
            ACTION_SYNC -> {
                if (view != null) {
                    runCatching { PanelChrome.onRefresh?.invoke() }
                    view?.state = PanelChrome.state
                    view?.invalidate()
                } else {
                    stopSelf()
                }
            }
            else -> open()
        }
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val was = view != null
        dismiss()
        measure()
        if (was) open()
    }

    private fun measure() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = wm.currentWindowMetrics
            sizeW = metrics.bounds.width()
            sizeH = metrics.bounds.height()
        } else {
            val dm = resources.displayMetrics
            sizeW = dm.widthPixels
            sizeH = dm.heightPixels
        }
    }

    private fun canDraw(): Boolean = runCatching { Settings.canDrawOverlays(this) }.getOrDefault(false)

    private fun open() {
        if (view != null) return
        if (!canDraw()) return
        measure()
        runCatching {
            runCatching { PanelChrome.onRefresh?.invoke() }
            val v = PanelView(this).apply {
                state = PanelChrome.state
                onAction = { action -> route(action) }
            }
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            val params = WindowManager.LayoutParams(
                sizeW,
                sizeH,
                type,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            wm.addView(v, params)
            view = v
            PanelChrome.visible = true
        }.onFailure {
            dismiss()
        }
    }

    private fun dismiss() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        PanelChrome.visible = false
        runCatching { PanelChrome.onClose?.invoke() }
        stopSelf()
    }

    /** Panel taps become one action string; plain state flips are handled here, the rest upstream. */
    private fun route(action: String) {
        val handledHere = when {
            action == "close" -> {
                dismiss()
                true
            }
            action == "rails:toggle" -> {
                // "hide the rails for now": the launcher decides what that means
                PanelSwitch.send(this, action)
                dismiss()
                true
            }
            else -> false
        }
        if (handledHere) return
        // every other action goes to the launcher; the panel is repainted when it changes state
        PanelSwitch.send(this, action)
    }

}
