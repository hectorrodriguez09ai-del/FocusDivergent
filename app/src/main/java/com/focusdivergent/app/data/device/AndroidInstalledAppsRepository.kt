package com.focusdivergent.app.data.device

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.focusdivergent.app.domain.model.InstalledApp
import com.focusdivergent.app.domain.repository.InstalledAppsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Las apps instaladas con icono de lanzador. La consulta está permitida por la declaración `<queries>` del
 * manifiesto, así que no hace falta el permiso `QUERY_ALL_PACKAGES`.
 */
class AndroidInstalledAppsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : InstalledAppsRepository {

    override suspend fun launchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(launcher, 0)
        }
        resolved
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(packageManager).toString()) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    override suspend fun labelOf(packageName: String): String? = withContext(Dispatchers.IO) {
        try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
}
