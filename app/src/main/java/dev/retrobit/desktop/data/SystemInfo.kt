package dev.retrobit.desktop.data

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import java.util.Locale

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
        // BatteryManager properties instead of registering for the sticky battery broadcast: this
        // snapshot is refreshed on a timer, and registering a receiver every second (which is what
        // the old code did) is both slower and heavier than asking the service.
        runCatching {
            val bm = ctx.getSystemService(BatteryManager::class.java)
            val capacity = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (capacity in 0..100) battery = capacity
            val status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
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
        // Locale.US: these are technical readouts, and "3,1G" on a comma-decimal device looks like a
        // corrupt number next to the ASCII bars
        return if (g >= 1) String.format(Locale.US, "%.1fG", g)
        else String.format(Locale.US, "%dM", b / (1024 * 1024))
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
