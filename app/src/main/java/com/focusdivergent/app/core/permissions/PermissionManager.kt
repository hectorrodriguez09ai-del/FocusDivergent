package com.focusdivergent.app.core.permissions

import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import com.focusdivergent.app.service.AppBlockerAccessibilityService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Comprueba el estado de los permisos críticos y sabe a qué pantalla de Ajustes llevar al usuario. */
@Singleton
class PermissionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun isGranted(permission: CriticalPermission): Boolean = when (permission) {
        CriticalPermission.OVERLAY -> Settings.canDrawOverlays(context)
        CriticalPermission.USAGE_STATS -> hasUsageStatsAccess()
        CriticalPermission.ACCESSIBILITY -> isAccessibilityServiceEnabled()
    }

    fun grantedPermissions(): Set<CriticalPermission> =
        CriticalPermission.entries.filterTo(mutableSetOf(), ::isGranted)

    /**
     * `SCHEDULE_EXACT_ALARM`: alarmas a la hora justa para el aviso de Obligaciones. No es un permiso crítico:
     * sin él el aviso usa una alarma aproximada, así que no forma parte de [CriticalPermission]. Antes de
     * Android 12 no hace falta pedirlo.
     */
    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Pantalla de Ajustes donde se concede el permiso de alarmas exactas. */
    fun exactAlarmSettingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }

    /**
     * ¿Está la app excluida de la optimización de batería estándar de Android? Es la mitad del ajuste: algunos
     * fabricantes (Samsung) tienen además su propia lista, que no se puede consultar. No requiere permiso.
     */
    fun isIgnoringBatteryOptimizations(): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /**
     * Pantalla de Ajustes con la lista de optimización de batería. Se abre la lista del sistema y es el usuario
     * quien elige; no se usa `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, que Google Play restringe.
     */
    fun batterySettingsIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    /** Intent a la pantalla de Ajustes donde se concede [permission]. */
    fun settingsIntent(permission: CriticalPermission): Intent = when (permission) {
        CriticalPermission.OVERLAY -> Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:${context.packageName}".toUri(),
        )
        CriticalPermission.USAGE_STATS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        CriticalPermission.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }

    // PACKAGE_USAGE_STATS es un permiso de tipo "app op": checkSelfPermission() no lo refleja,
    // hay que preguntar a AppOpsManager. Las dos variantes de la consulta están marcadas como
    // obsoletas en algún nivel de API, pero son la única forma de comprobarlo.
    @Suppress("DEPRECATION")
    private fun hasUsageStatsAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    // El sistema guarda los servicios de accesibilidad activos como una lista de componentes
    // separados por ':'.
    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(context, AppBlockerAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabledServices.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }
}
