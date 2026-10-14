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
    /** Both services are always present on a phone/tablet, but never trust a platform lookup. */
    private val launcherApps: LauncherApps? = context.getSystemService(LauncherApps::class.java)
    private val userManager: UserManager? = context.getSystemService(UserManager::class.java)

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps

    suspend fun reload() = withContext(Dispatchers.IO) {
        val me = Process.myUserHandle()
        val result = ArrayList<AppEntry>()
        val launcher = launcherApps ?: return@withContext
        val profiles: List<UserHandle> = runCatching { userManager?.userProfiles }.getOrNull()
            ?.takeIf { it.isNotEmpty() } ?: listOf(me)
        for (user in profiles) {
            val serial = runCatching { userManager?.getSerialNumberForUser(user) }.getOrNull() ?: 0L
            val list = runCatching { launcher.getActivityList(null, user) }.getOrDefault(emptyList())
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
        // Clearing the whole icon cache on every reload (which happens on every onResume and on every
        // package change) made the desktop re-render every icon for nothing. Only a changed app set —
        // an install, an uninstall, a renamed package — invalidates the pictures.
        if (result.map { it.key } != _apps.value.map { it.key }) IconCache.clear()
        _apps.value = result
    }

    fun registerChanges(onChange: () -> Unit) {
        val launcher = launcherApps ?: return
        launcher.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) = onChange()
            override fun onPackageAdded(packageName: String?, user: UserHandle?) = onChange()
            override fun onPackageChanged(packageName: String?, user: UserHandle?) = onChange()
            override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = onChange()
            override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = onChange()
        }, Handler(Looper.getMainLooper()))
    }

    /**
     * Launches an app. Apps open **full screen**: this desktop never asks the system for a floating
     * window, so there is no rectangle to send and no task to recreate - the frame the user sees
     * around the app is drawn by the cyberdeck chrome (see shell/ChromeService).
     */
    fun launch(app: AppEntry): Boolean = try {
        val launcher = launcherApps ?: throw IllegalStateException("no launcher service")
        launcher.startMainActivity(app.component, app.user, null, null)
        true
    } catch (t: Throwable) {
        runCatching {
            val i = context.packageManager.getLaunchIntentForPackage(app.pkg)
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
                true
            } else false
        }.getOrDefault(false)
    }

    fun openAppInfo(app: AppEntry) {
        runCatching { launcherApps?.startAppDetailsActivity(app.component, app.user, null, null) }
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
