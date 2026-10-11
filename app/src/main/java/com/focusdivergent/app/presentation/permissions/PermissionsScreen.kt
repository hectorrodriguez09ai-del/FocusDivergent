package com.focusdivergent.app.presentation.permissions

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusdivergent.app.R
import com.focusdivergent.app.core.permissions.BatteryGuidance
import com.focusdivergent.app.core.permissions.CriticalPermission
import com.focusdivergent.app.presentation.components.PrimaryActionButton
import com.focusdivergent.app.presentation.components.ScreenScaffold
import com.focusdivergent.app.presentation.theme.Spacing
import com.focusdivergent.app.service.FocusBlockerService

/**
 * Guía para conceder los tres permisos críticos, los dos ajustes recomendados y activar la protección.
 *
 * Tiene dos usos: como bienvenida en el primer arranque ([onDone] no nulo: botón para entrar en la app, que no
 * exige haber concedido nada) y como pantalla secundaria desde «Bloqueos» ([onBack] no nulo: flecha de volver).
 */
@Composable
fun PermissionsScreen(
    onDone: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    viewModel: PermissionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Con o sin permiso de notificaciones la protección se activa: el aviso fijo es un extra, no un requisito.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { FocusBlockerService.start(context) }

    // Los permisos se conceden en Ajustes, fuera de la app: al volver se recomprueba el estado.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    ScreenScaffold(
        title = stringResource(if (onDone != null) R.string.app_name else R.string.blocks_permissions_title),
        onBack = onBack,
        bottomBar = {
            if (onDone != null) {
                // Entrar no depende de los permisos: las tareas funcionan sin ellos y se pueden conceder después.
                PrimaryActionButton(
                    text = stringResource(
                        if (uiState.allGranted) R.string.onboarding_enter else R.string.onboarding_enter_later,
                    ),
                    onClick = onDone,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = Spacing.screen, vertical = Spacing.md),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = stringResource(R.string.permissions_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.permissions_intro),
                style = MaterialTheme.typography.bodyLarge,
            )

            CriticalPermission.entries.forEach { permission ->
                PermissionCard(
                    title = stringResource(permission.titleRes),
                    description = stringResource(permission.descriptionRes),
                    granted = permission in uiState.granted,
                    onGrant = { context.openSettings(viewModel.settingsIntent(permission)) },
                )
            }

            // Opcional: no condiciona la protección, solo la puntualidad del aviso de Obligaciones.
            PermissionCard(
                title = stringResource(R.string.permission_exact_alarm_title),
                description = stringResource(R.string.permission_exact_alarm_description),
                granted = uiState.exactAlarmsGranted,
                onGrant = { context.openSettings(viewModel.exactAlarmSettingsIntent()) },
            )

            BatteryCard(
                ignored = uiState.batteryOptimizationIgnored,
                guidance = remember { BatteryGuidance.forManufacturer(Build.MANUFACTURER) },
                onOpenSettings = { context.openSettings(viewModel.batterySettingsIntent()) },
            )

            if (uiState.allGranted) {
                ProtectionCard(
                    running = uiState.isProtectionRunning,
                    onToggle = {
                        when {
                            uiState.isProtectionRunning -> FocusBlockerService.stop(context)
                            context.needsNotificationPermission() ->
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else -> FocusBlockerService.start(context)
                        }
                    },
                )
            } else {
                Text(
                    text = stringResource(R.string.permissions_restricted_settings_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (granted) {
                Text(
                    text = stringResource(R.string.permission_granted),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
            } else {
                Button(onClick = onGrant) {
                    Text(stringResource(R.string.permission_grant_action))
                }
            }
        }
    }
}

/**
 * Guía para que el sistema no pare la protección (RNF-2). Es opcional y solo orienta: abre la pantalla de
 * Ajustes del sistema y explica los pasos, porque cada fabricante esconde su propia lista.
 */
@Composable
private fun BatteryCard(
    ignored: Boolean,
    guidance: BatteryGuidance,
    onOpenSettings: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.battery_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.battery_description),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (ignored) {
                Text(
                    text = stringResource(R.string.battery_standard_done),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
            } else {
                Text(
                    text = stringResource(R.string.battery_steps_standard),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onOpenSettings) {
                    Text(stringResource(R.string.battery_open_settings))
                }
            }
            if (guidance == BatteryGuidance.SAMSUNG) {
                Text(
                    text = stringResource(R.string.battery_steps_samsung),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ProtectionCard(
    running: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.protection_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.protection_description),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (running) {
                Text(
                    text = stringResource(R.string.protection_active),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
                OutlinedButton(onClick = onToggle) {
                    Text(stringResource(R.string.protection_deactivate))
                }
            } else {
                Button(onClick = onToggle) {
                    Text(stringResource(R.string.protection_activate))
                }
            }
        }
    }
}

private fun Context.needsNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED

// Algunos fabricantes no incluyen todas las pantallas de Ajustes; en ese caso se abre la general.
private fun Context.openSettings(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}

@get:StringRes
private val CriticalPermission.titleRes: Int
    get() = when (this) {
        CriticalPermission.OVERLAY -> R.string.permission_overlay_title
        CriticalPermission.USAGE_STATS -> R.string.permission_usage_stats_title
        CriticalPermission.ACCESSIBILITY -> R.string.permission_accessibility_title
    }

@get:StringRes
private val CriticalPermission.descriptionRes: Int
    get() = when (this) {
        CriticalPermission.OVERLAY -> R.string.permission_overlay_description
        CriticalPermission.USAGE_STATS -> R.string.permission_usage_stats_description
        CriticalPermission.ACCESSIBILITY -> R.string.permission_accessibility_description
    }
