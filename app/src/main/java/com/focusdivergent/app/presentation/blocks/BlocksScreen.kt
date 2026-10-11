package com.focusdivergent.app.presentation.blocks

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusdivergent.app.R
import com.focusdivergent.app.domain.model.AppLimit
import com.focusdivergent.app.domain.model.FrictionLevel
import com.focusdivergent.app.domain.model.PendingLimitChange
import com.focusdivergent.app.domain.model.friction
import com.focusdivergent.app.presentation.components.AppIcon
import com.focusdivergent.app.presentation.components.EmptyState
import com.focusdivergent.app.presentation.components.LinkCard
import com.focusdivergent.app.presentation.components.PrimaryActionButton
import com.focusdivergent.app.presentation.components.ScreenScaffold
import com.focusdivergent.app.presentation.components.SectionHeader
import com.focusdivergent.app.presentation.theme.FocusTheme
import com.focusdivergent.app.presentation.theme.Spacing

private const val MILLIS_PER_MINUTE = 60_000L

/** Pestaña «Bloqueos»: las apps con límite, su uso de hoy y el acceso a añadir otra o a los permisos. */
@Composable
fun BlocksScreen(
    onAddApp: () -> Unit,
    onEditLimit: (packageName: String) -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenClosure: () -> Unit,
    onOpenAi: () -> Unit = {},
    viewModel: BlocksViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    BlocksContent(
        uiState = uiState,
        onAddApp = onAddApp,
        onEditLimit = onEditLimit,
        onOpenPermissions = onOpenPermissions,
        onOpenClosure = onOpenClosure,
        onOpenAi = onOpenAi,
    )
}

@Composable
fun BlocksContent(
    uiState: BlocksUiState,
    onAddApp: () -> Unit,
    onEditLimit: (packageName: String) -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenClosure: () -> Unit = {},
    onOpenAi: () -> Unit = {},
) {
    ScreenScaffold(
        title = stringResource(R.string.nav_blocks),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddApp,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.limit_add_app)) },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            // Hueco al final para que el botón flotante no tape la última tarjeta.
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item(key = "protection") {
                ProtectionStatus(running = uiState.protectionRunning, onOpenPermissions = onOpenPermissions)
            }

            if (uiState.items.isNotEmpty()) {
                item(key = "header") { SectionHeader(stringResource(R.string.limits_header)) }
                items(uiState.items, key = { it.limit.packageName }) { item ->
                    LimitCard(item = item, onClick = { onEditLimit(item.limit.packageName) })
                }
            } else if (!uiState.loading) {
                item(key = "empty") {
                    EmptyState(
                        title = stringResource(R.string.limits_empty_title),
                        message = stringResource(R.string.limits_empty_message),
                        action = { PrimaryActionButton(stringResource(R.string.limits_empty_action), onAddApp) },
                    )
                }
            }

            item(key = "links") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.md)) {
                    LinkCard(
                        title = stringResource(R.string.blocks_closure_title),
                        description = stringResource(R.string.blocks_closure_description),
                        onClick = onOpenClosure,
                    )
                    LinkCard(
                        title = stringResource(R.string.blocks_ai_title),
                        description = stringResource(R.string.blocks_ai_description),
                        onClick = onOpenAi,
                    )
                    LinkCard(
                        title = stringResource(R.string.blocks_permissions_title),
                        description = stringResource(R.string.blocks_permissions_description),
                        onClick = onOpenPermissions,
                    )
                }
            }
        }
    }
}

/**
 * Si la protección está pausada, los límites no se aplican: se dice con claridad y con el camino para activarla.
 * Si está activa, una sola línea tranquila.
 */
@Composable
private fun ProtectionStatus(running: Boolean, onOpenPermissions: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (running) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.errorContainer,
        contentColor = if (running) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = stringResource(if (running) R.string.protection_status_on else R.string.protection_status_off),
                style = MaterialTheme.typography.titleMedium,
            )
            if (!running) {
                Text(
                    text = stringResource(R.string.protection_status_off_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onOpenPermissions) { Text(stringResource(R.string.protection_status_activate)) }
            }
        }
    }
}

/** Una app con límite: nombre, qué se ha elegido, cuánto lleva hoy y, si lo hay, el cambio que espera a mañana. */
@Composable
private fun LimitCard(item: LimitItem, onClick: () -> Unit) {
    val limit = item.limit
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            AppIcon(limit.packageName, size = 44.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(text = item.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = limitSummary(limit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!limit.isStrictlyBlocked) UsageBar(usedMillis = item.usedMillis, allowedMinutes = limit.dailyMinutesAllowed)
                item.pending?.let { PendingNote(it) }
            }
        }
    }
}

/**
 * Cuánto lleva de lo permitido. Una barra neutra: al llegar al 100 % no cambia de color ni se pone en alerta,
 * simplemente está llena (RNF-3).
 */
@Composable
private fun UsageBar(usedMillis: Long, allowedMinutes: Int) {
    val usedMinutes = (usedMillis / MILLIS_PER_MINUTE).toInt()
    val text = stringResource(R.string.limit_usage_today, usedMinutes, allowedMinutes)
    val progress = (usedMillis.toFloat() / (allowedMinutes * MILLIS_PER_MINUTE)).coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        // La barra es un adorno del texto de al lado: el lector de pantalla lee solo el texto.
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { },
            color = MaterialTheme.colorScheme.primary,
            trackColor = FocusTheme.colors.track,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PendingNote(pending: PendingLimitChange) {
    val newLimit = pending.newLimit
    val text = if (newLimit == null) {
        stringResource(R.string.limit_pending_removal)
    } else {
        stringResource(R.string.limit_pending_change, limitSummary(newLimit))
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        // Ámbar, el color de aviso de la app: informa, no alarma.
        color = MaterialTheme.colorScheme.error,
    )
}

/** «30 min · Flexible», o «Siempre en pausa» para una app cerrada. */
@Composable
internal fun limitSummary(limit: AppLimit): String {
    val base = if (limit.isStrictlyBlocked) {
        stringResource(R.string.friction_closed)
    } else {
        stringResource(R.string.limit_summary, limit.dailyMinutesAllowed, stringResource(limit.friction.labelRes))
    }
    return if (limit.isLeisure) stringResource(R.string.limit_summary_leisure, base) else base
}

@get:StringRes
internal val FrictionLevel.labelRes: Int
    get() = when (this) {
        FrictionLevel.FLEXIBLE -> R.string.friction_flexible
        FrictionLevel.FIRM -> R.string.friction_firm
        FrictionLevel.CLOSED -> R.string.friction_closed
    }
