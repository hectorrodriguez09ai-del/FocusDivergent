package com.focusdivergent.app.presentation.permissions

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusdivergent.app.core.permissions.CriticalPermission
import com.focusdivergent.app.core.permissions.PermissionManager
import com.focusdivergent.app.service.BlockerStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * @property exactAlarmsGranted permiso opcional de alarmas exactas; no cuenta para [allGranted] porque sin él
 * la protección funciona igual y solo el aviso de Obligaciones puede retrasarse unos minutos.
 * @property batteryOptimizationIgnored la app está excluida de la optimización de batería estándar. También es
 * opcional y no cuenta para [allGranted]: solo reduce el riesgo de que el sistema pare la protección.
 */
data class PermissionsUiState(
    val granted: Set<CriticalPermission> = emptySet(),
    val exactAlarmsGranted: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val isProtectionRunning: Boolean = false,
) {
    val allGranted: Boolean get() = granted.size == CriticalPermission.entries.size
}

/** Lo que Android permite consultar, agrupado para refrescarlo de una vez. */
private data class SystemState(
    val granted: Set<CriticalPermission>,
    val exactAlarms: Boolean,
    val batteryIgnored: Boolean,
)

@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val permissionManager: PermissionManager,
    blockerStatus: BlockerStatus,
) : ViewModel() {

    private fun readSystemState() = SystemState(
        granted = permissionManager.grantedPermissions(),
        exactAlarms = permissionManager.canScheduleExactAlarms(),
        batteryIgnored = permissionManager.isIgnoringBatteryOptimizations(),
    )

    private val system = MutableStateFlow(readSystemState())

    val uiState: StateFlow<PermissionsUiState> = combine(system, blockerStatus.isRunning) { system, running ->
        system.toUiState(running)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = system.value.toUiState(blockerStatus.isRunning.value),
    )

    /** Android no avisa cuando cambian estos permisos: hay que volver a consultarlos. */
    fun refresh() {
        system.value = readSystemState()
    }

    fun settingsIntent(permission: CriticalPermission): Intent =
        permissionManager.settingsIntent(permission)

    fun exactAlarmSettingsIntent(): Intent = permissionManager.exactAlarmSettingsIntent()

    fun batterySettingsIntent(): Intent = permissionManager.batterySettingsIntent()

    private fun SystemState.toUiState(running: Boolean) = PermissionsUiState(
        granted = granted,
        exactAlarmsGranted = exactAlarms,
        batteryOptimizationIgnored = batteryIgnored,
        isProtectionRunning = running,
    )
}
