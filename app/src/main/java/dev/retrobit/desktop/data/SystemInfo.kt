package dev.retrobit.desktop.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import androidx.core.content.ContextCompat

data class SysSnapshot(
    val battery: Int = -1,
    val charging: Boolean = false,
    val memUsed: Long = 0,
    val memTotal: Long = 1,
    val diskUsed: Long = 0,
    val diskTotal: Long = 1,
    val uptimeMs: Long = 0,
    val net: String = "----",
) {
    val memFrac: Float get() = (memUsed.toFloat() / memTotal.coerceAtLeast(1)).coerceIn(0f, 1f)
    val diskFrac: Float get() = (diskUsed.toFloat() / diskTotal.coerceAtLeast(1)).coerceIn(0f, 1f)
    val batFrac: Float get() = (battery.coerceAtLeast(0) / 100f).coerceIn(0f, 1f)
}

object SystemInfo {
    fun snapshot(ctx: Context): SysSnapshot {
        var battery = -1
        var charging = false
        runCatching {
            val i: Intent? = ContextCompat.registerReceiver(
                ctx, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            if (i != null) {
                val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (level >= 0 && scale > 0) battery = level * 100 / scale
                val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        var memUsed = 0L
        var memTotal = 1L
        runCatching {
            val am = ctx.getSystemService(ActivityManager::class.java)
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            memTotal = mi.totalMem
            memUsed = mi.totalMem - mi.availMem
        }
        var diskUsed = 0L
        var diskTotal = 1L
        runCatching {
            val st = StatFs(Environment.getDataDirectory().path)
            diskTotal = st.totalBytes
            diskUsed = st.totalBytes - st.availableBytes
        }
        var net = "OFFLINE"
        runCatching {
            val cm = ctx.getSystemService(ConnectivityManager::class.java)
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            if (caps != null) {
                net = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELL"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETH"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                    else -> "NET"
                }
            }
        }
        return SysSnapshot(battery, charging, memUsed, memTotal, diskUsed, diskTotal, SystemClock.elapsedRealtime(), net)
    }

    fun fmtBytes(b: Long): String {
        val g = b / (1024.0 * 1024 * 1024)
        return if (g >= 1) String.format("%.1fG", g) else String.format("%dM", b / (1024 * 1024))
    }

    fun fmtUptime(ms: Long): String {
        val m = ms / 60000
        val d = m / (60 * 24)
        val h = (m / 60) % 24
        val mm = m % 60
        return if (d > 0) "${d}d ${h}h ${mm}m" else "${h}h ${mm}m"
    }

    /** ASCII progress bar: [#####-----] */
    fun bar(frac: Float, width: Int = 10): String {
        val n = (frac.coerceIn(0f, 1f) * width).toInt()
        return "[" + "#".repeat(n) + "-".repeat(width - n) + "]"
    }
}
