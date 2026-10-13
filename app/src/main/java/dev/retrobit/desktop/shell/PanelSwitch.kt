package dev.retrobit.desktop.shell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

/**
 * The wire between the launcher and its overlay services.
 *
 * The rails and the quick panel live in their own processes-facing overlay windows and are drawn by
 * plain views, so they cannot read the view model directly. Everything the user does on them turns
 * into one short string on this bus, the launcher turns it into a call, and the resulting state is
 * pushed back through [CyberdeckChrome] / [PanelChrome].
 *
 * The broadcast is private to the app (`setPackage` + a not-exported receiver), so nothing else on
 * the tablet can drive the launcher.
 */
class PanelSwitch(private val onAction: (String) -> Unit) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        runCatching { onAction(action) }
    }

    companion object {
        const val ACTION = "dev.retrobit.desktop.PANEL"
        const val EXTRA_ACTION = "action"

        fun register(ctx: Context, receiver: PanelSwitch) {
            runCatching {
                ContextCompat.registerReceiver(
                    ctx,
                    receiver,
                    IntentFilter(ACTION),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
            }
        }

        fun unregister(ctx: Context, receiver: PanelSwitch) {
            runCatching { ctx.unregisterReceiver(receiver) }
        }

        /** Sends one action to the launcher: "bright:+", "rotate:toggle", "app:com.foo", ... */
        fun send(ctx: Context, action: String) {
            runCatching {
                ctx.sendBroadcast(
                    Intent(ACTION).setPackage(ctx.packageName).putExtra(EXTRA_ACTION, action),
                )
            }
        }
    }
}
