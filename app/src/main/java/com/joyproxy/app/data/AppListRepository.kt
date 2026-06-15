package com.joyproxy.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: android.graphics.drawable.Drawable?,
)

class AppListRepository(private val context: Context) {
    suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcherIntent =
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
        val apps =
            pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
                .mapNotNull { resolveInfo ->
                    val appInfo = resolveInfo.activityInfo.applicationInfo
                    AppInfo(
                        packageName = appInfo.packageName,
                        label = resolveInfo.loadLabel(pm).toString(),
                        icon = runCatching { resolveInfo.loadIcon(pm) }.getOrNull(),
                    )
                }
                .distinctBy { it.packageName }

        if (apps.isNotEmpty()) {
            apps.sortedBy { it.label.lowercase() }
        } else {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .map { app ->
                    AppInfo(
                        packageName = app.packageName,
                        label = app.loadLabel(pm).toString(),
                        icon = runCatching { app.loadIcon(pm) }.getOrNull(),
                    )
                }
                .sortedBy { it.label.lowercase() }
        }
    }
}
