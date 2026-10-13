package dev.retrobit.desktop.data

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import dev.retrobit.desktop.model.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps

    suspend fun reload() = withContext(Dispatchers.IO) {
        val me = Process.myUserHandle()
        val result = ArrayList<AppEntry>()
        val profiles: List<UserHandle> = runCatching { userManager.userProfiles }.getOrDefault(listOf(me))
        for (user in profiles) {
            val serial = runCatching { userManager.getSerialNumberForUser(user) }.getOrDefault(0L)
            val list = runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList())
            for (info in list) {
                val cn = info.componentName
                if (cn.packageName == context.packageName) continue
                result += AppEntry(
                    key = cn.flattenToShortString() + "#" + serial,
                    label = info.label?.toString()?.trim().orEmpty().ifEmpty { cn.packageName },
                    pkg = cn.packageName,
                    component = cn,
                    user = user,
                    info = info,
                    isWorkProfile = user != me,
                )
            }
        }
        result.sortWith(compareBy<AppEntry> { it.label.lowercase() }.thenBy { it.isWorkProfile })
        IconCache.clear()
        _apps.value = result
    }

    fun registerChanges(onChange: () -> Unit) {
        launcherApps.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) = onChange()
            override fun onPackageAdded(packageName: String?, user: UserHandle?) = onChange()
            override fun onPackageChanged(packageName: String?, user: UserHandle?) = onChange()
            override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = onChange()
            override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = onChange()
        }, Handler(Looper.getMainLooper()))
    }

    /**
     * Launches an app. [bounds] is the rectangle the app should occupy when the device supports
     * floating / freeform windows ([activityOptionsFor]); the outer Rect is also passed as the
     * launch source bounds so the system animates it from our window.
     */
    fun launch(app: AppEntry, bounds: Rect? = null, options: android.os.Bundle? = null): Boolean = try {
        launcherApps.startMainActivity(app.component, app.user, bounds, options)
        true
    } catch (t: Throwable) {
        // Fallback: plain launch intent, with the floating bounds when we have them.
        runCatching {
            val i = context.packageManager.getLaunchIntentForPackage(app.pkg)
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (options != null) context.startActivity(i, options) else context.startActivity(i)
                true
            } else false
        }.getOrDefault(false)
    }

    fun openAppInfo(app: AppEntry) {
        runCatching { launcherApps.startAppDetailsActivity(app.component, app.user, null, null) }
    }

    fun uninstall(app: AppEntry) {
        runCatching {
            val i = Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.pkg))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        }
    }

    /** Package name of the default browser, used to seed the dock on first run. */
    fun defaultBrowser(): String? = runCatching {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        context.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }.getOrNull()?.takeIf { it != "android" }
}
