package com.aravind.habitmaker.data.apps

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class InstalledApp(val packageName: String, val label: String)

/** Apps that show in the launcher (declared through <queries> in the manifest). */
class InstalledApps(private val context: Context) {
    private val pm get() = context.packageManager
    private val icons = ConcurrentHashMap<String, Bitmap>()
    private val labels = ConcurrentHashMap<String, String>()
    @Volatile private var cached: List<InstalledApp>? = null

    suspend fun list(): List<InstalledApp> = cached ?: withContext(Dispatchers.IO) {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .map { InstalledApp(it, label(it)) }
            .sortedBy { it.label.lowercase() }
            .also { cached = it }
    }

    fun label(pkg: String): String = labels.getOrPut(pkg) {
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    }

    suspend fun icon(pkg: String): Bitmap? = icons[pkg] ?: withContext(Dispatchers.IO) {
        runCatching { pm.getApplicationIcon(pkg).toBitmapSafe() }.getOrNull()?.also { icons[pkg] = it }
    }

    private fun Drawable.toBitmapSafe(): Bitmap = toBitmap(96, 96)
}
