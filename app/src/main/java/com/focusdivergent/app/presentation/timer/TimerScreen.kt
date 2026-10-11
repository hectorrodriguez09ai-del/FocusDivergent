package com.focusdivergent.app.presentation.timer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusdivergent.app.R
import com.focusdivergent.app.domain.timer.TimerLimits
import com.focusdivergent.app.domain.timer.TimerMode
import com.focusdivergent.app.domain.timer.TimerPhaseKind
import com.focusdivergent.app.domain.timer.TimerPlan
import com.focusdivergent.app.domain.timer.TimerState
import com.focusdivergent.app.presentation.components.ChoiceCard
import com.focusdivergent.app.presentation.components.MinutesPicker
import com.focusdivergent.app.presentation.components.PrimaryActionButton
import com.focusdivergent.app.presentation.components.ScreenScaffold
import com.focusdivergent.app.presentation.components.SecondaryActionButton
import com.focusdivergent.app.presentation.components.SectionHeader
import com.focusdivergent.app.presentation.theme.LocalReducedMotion
import com.focusdivergent.app.presentation.theme.Sizes
import com.focusdivergent.app.presentation.theme.Spacing
import com.focusdivergent.app.presentation.theme.tabularNumbers

/**
 * Temporizador visual (RF-4.1): elegir una cuenta atrás o un Pomodoro, verla vaciarse y cerrar la sesión.
 * La sesión vive en disco y en una notificación con cuenta atrás del sistema, así que la pantalla se puede cerrar.
 */
@Composable
fun TimerScreen(
    onBack: () -> Unit,
    onAudio: () -> Unit = {},
    viewModel: TimerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                TimerEvent.Close -> onBack()
            }
        }
    }

    // Sin el permiso, el temporizador funciona igual pero no puede avisar con la app cerrada: se pide antes de
    // empezar y se empieza se conteste lo que se conteste.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.start()
    }

    TimerContent(
        uiState = uiState,
        onBack = onBack,
        onKind = viewModel::setKind,
        onSingleMinutes = viewModel::setSingleMinutes,
        onFocusMinutes = viewModel::setFocusMinutes,
        onBreakMinutes = viewModel::setBreakMinutes,
        onCycles = viewModel::setCycles,
        onStart = {
            if (context.needsNotificationPermission()) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.start()
            }
        },
        onStop = viewModel::stop,
        onFinish = viewModel::finish,
        onAudio = onAudio,
    )
}

private fun Context.needsNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

@Composable
fun TimerContent(
    uiState: TimerUiState,
    onBack: () -> Unit,
    onKind: (TimerKind) -> Unit,
    onSingleMinutes: (Int) -> Unit,
    onFocusMinutes: (Int) -> Unit,
    onBreakMinutes: (Int) -> Unit,
    onCycles: (Int) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onFinish: (taskDone: Boolean) -> Unit,
    onAudio: () -> Unit = {},
) {
    ScreenScaffold(title = stringResource(R.string.timer_title), onBack = onBack) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            when (val state = uiState.state) {
                null -> TimerSetup(uiState, onKind, onSingleMinutes, onFocusMinutes, onBreakMinutes, onCycles, onStart, onAudio)
                is TimerState.Running -> TimerRunning(uiState, state, onStop, onAudio)
                is TimerState.Finished -> TimerFinished(uiState, onFinish)
            }
        }
    }
}

// ---- Elegir

@Composable
private fun TimerSetup(
    uiState: TimerUiState,
    onKind: (TimerKind) -> Unit,
    onSingleMinutes: (Int) -> Unit,
    onFocusMinutes: (Int) -> Unit,
    onBreakMinutes: (Int) -> Unit,
    onCycles: (Int) -> Unit,
    onStart: () -> Unit,
    onAudio: () -> Unit,
) {
    uiState.task?.let { task ->
        Text(
            text = stringResource(R.string.timer_for_task, task.title),
            style = MaterialTheme.typography.titleMedium,
        )
        uiState.steps.next?.let { NextStepLine(it.text) }
    }

    Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ChoiceCard(
            title = stringResource(R.string.timer_kind_single),
            description = stringResource(R.string.timer_kind_single_description),
            selected = uiState.kind == TimerKind.SINGLE,
            onClick = { onKind(TimerKind.SINGLE) },
        )
        ChoiceCard(
            title = stringResource(R.string.timer_kind_pomodoro),
            description = stringResource(R.string.timer_kind_pomodoro_description),
            selected = uiState.kind == TimerKind.POMODORO,
            onClick = { onKind(TimerKind.POMODORO) },
        )
    }

    when (uiState.kind) {
        TimerKind.SINGLE -> {
            SectionHeader(stringResource(R.string.timer_how_long))
            MinutesPicker(
                minutes = uiState.single.minutes,
                onChange = onSingleMinutes,
                presets = TimerLimits.SINGLE_PRESETS,
                min = TimerLimits.SINGLE_MIN,
                max = TimerLimits.SINGLE_MAX,
            )
        }
        TimerKind.POMODORO -> {
            SectionHeader(stringResource(R.string.timer_focus_length))
            MinutesPicker(
                minutes = uiState.pomodoro.focusMinutes,
                onChange = onFocusMinutes,
                presets = listOf(25, 45, 60),
                min = TimerLimits.FOCUS_MIN,
                max = TimerLimits.FOCUS_MAX,
            )
            SectionHeader(stringResource(R.string.timer_break_length))
            MinutesPicker(
                minutes = uiState.pomodoro.breakMinutes,
                onChange = onBreakMinutes,
                presets = listOf(5, 10, 20),
                min = TimerLimits.BREAK_MIN,
                max = TimerLimits.BREAK_MAX,
            )
            SectionHeader(stringResource(R.string.timer_cycles))
            CountStepper(
                value = uiState.pomodoro.cycles,
                onChange = onCycles,
                min = TimerLimits.CYCLES_MIN,
                max = TimerLimits.CYCLES_MAX,
            )
            Text(
                text = pomodoroSummary(uiState.pomodoro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        PrimaryActionButton(text = stringResource(R.string.timer_start), onClick = onStart)
        Text(
            text = stringResource(R.string.timer_start_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    AudioLink(onAudio)
}

/** Atajo al sonido ambiente: el ruido de fondo y el temporizador suelen ir juntos. */
@Composable
private fun AudioLink(onAudio: () -> Unit) {
    TextButton(onClick = onAudio, modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
        Text(stringResource(R.string.timer_audio), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun pomodoroSummary(mode: TimerMode.Pomodoro): String {
    val total = TimerPlan.totalDuration(mode).toMinutes().toInt()
    val totalText = if (total >= 60 && total % 60 != 0) {
        stringResource(R.string.timer_duration_hours_minutes, total / 60, total % 60)
    } else if (total >= 60) {
        stringResource(R.string.timer_duration_hours, total / 60)
    } else {
        stringResource(R.string.minutes_value, total)
    }
    return pluralStringResource(R.plurals.timer_pomodoro_summary, mode.cycles, mode.cycles, mode.focusMinutes, mode.breakMinutes, totalText)
}

/** − valor + para elegir un número pequeño (los bloques de enfoque). */
@Composable
private fun CountStepper(value: Int, onChange: (Int) -> Unit, min: Int, max: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        val less = stringResource(R.string.timer_cycles_less)
        OutlinedButton(
            onClick = { onChange((value - 1).coerceAtLeast(min)) },
            enabled = value > min,
            modifier = Modifier
                .heightIn(min = Sizes.primaryAction)
                .widthIn(min = Sizes.primaryAction)
                .semantics { contentDescription = less },
        ) { Text("−") }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineMedium.tabularNumbers(),
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        val more = stringResource(R.string.timer_cycles_more)
        OutlinedButton(
            onClick = { onChange((value + 1).coerceAtMost(max)) },
            enabled = value < max,
            modifier = Modifier
                .heightIn(min = Sizes.primaryAction)
                .widthIn(min = Sizes.primaryAction)
                .semantics { contentDescription = more },
        ) { Text("+") }
    }
}

// ---- En marcha

@Composable
private fun TimerRunning(uiState: TimerUiState, state: TimerState.Running, onStop: () -> Unit, onAudio: () -> Unit) {
    val isBreak = state.phase.kind == TimerPhaseKind.BREAK
    val title = when {
        isBreak -> stringResource(R.string.timer_phase_break)
        state.focusTotal > 1 -> stringResource(R.string.timer_phase_focus_of, state.focusNumber, state.focusTotal)
        else -> stringResource(R.string.timer_phase_focus)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        uiState.session?.taskTitle?.let {
            Text(text = it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        uiState.steps.next?.let { NextStepLine(it.text) }
    }

    TimerPie(
        fraction = state.fractionRemaining,
        remainingSeconds = state.remainingSeconds,
        color = if (isBreak) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
    )

    Text(
        text = nextPhaseText(uiState, state),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )

    SecondaryActionButton(text = stringResource(R.string.timer_stop), onClick = onStop)
    AudioLink(onAudio)
}

@Composable
private fun nextPhaseText(uiState: TimerUiState, state: TimerState.Running): String {
    val session = uiState.session ?: return ""
    val next = TimerPlan.phases(session.mode).getOrNull(state.phaseIndex + 1)
        ?: return stringResource(R.string.timer_last_phase)
    return if (next.kind == TimerPhaseKind.BREAK) {
        stringResource(R.string.timer_next_break, next.minutes)
    } else {
        stringResource(R.string.timer_next_focus, next.minutes)
    }
}

/**
 * La «tarta» que se vacía con el tiempo, con la cuenta atrás en cifras grandes de ancho fijo en el centro.
 *
 * Sin rojo, sin parpadeos y sin aceleración al final: el último minuto es igual de tranquilo que el primero (RNF-3).
 * Con el movimiento reducido del sistema no se interpola: se redibuja cada segundo. Para los lectores de pantalla
 * se dice el tiempo en minutos, que cambia una vez por minuto en vez de anunciar cada segundo.
 */
@Composable
internal fun TimerPie(
    fraction: Float,
    remainingSeconds: Long,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val drawn = if (reduced) {
        fraction
    } else {
        animateFloatAsState(targetValue = fraction, animationSpec = tween(durationMillis = 1000, easing = LinearEasing), label = "timer-fraction").value
    }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    // El centro se pinta del color del fondo: las cifras se leen siempre con el contraste máximo del tema.
    val center = MaterialTheme.colorScheme.background
    val minutes = spokenMinutes(remainingSeconds)
    val description = pluralStringResource(R.plurals.timer_remaining_description, minutes, minutes)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 320.dp)
            .aspectRatio(1f)
            .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val diameter = size.minDimension
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(color = track, startAngle = 0f, sweepAngle = 360f, useCenter = true, topLeft = topLeft, size = arcSize, style = Fill)
            // Empieza arriba y se vacía en el sentido de las agujas del reloj.
            drawArc(color = color, startAngle = -90f, sweepAngle = 360f * drawn.coerceIn(0f, 1f), useCenter = true, topLeft = topLeft, size = arcSize, style = Fill)
            drawCircle(color = center, radius = diameter * CENTER_RATIO / 2f)
        }
        Text(
            text = formatClock(remainingSeconds),
            style = MaterialTheme.typography.displayMedium.tabularNumbers(),
            textAlign = TextAlign.Center,
        )
    }
}

/** Qué parte del diámetro ocupa el centro libre donde van las cifras. */
private const val CENTER_RATIO = 0.66f

// ---- Terminada

@Composable
private fun TimerFinished(uiState: TimerUiState, onFinish: (Boolean) -> Unit) {
    val taskTitle = uiState.session?.taskTitle
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(text = stringResource(R.string.timer_finished_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            text = stringResource(R.string.timer_finished_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }

    if (taskTitle != null && uiState.session.taskId != null) {
        Text(
            text = stringResource(R.string.timer_finished_task_question, taskTitle),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        // Las dos respuestas pesan igual: no hay una «buena».
        SecondaryActionButton(text = stringResource(R.string.timer_finished_task_yes), onClick = { onFinish(true) })
        SecondaryActionButton(text = stringResource(R.string.timer_finished_task_no), onClick = { onFinish(false) })
    } else {
        PrimaryActionButton(text = stringResource(R.string.timer_finished_close), onClick = { onFinish(false) })
    }
}

/** Mientras se trabaja con el temporizador, el paso que toca (no la lista): una sola cosa a la que mirar. */
@Composable
private fun NextStepLine(text: String) {
    Text(
        text = stringResource(R.string.timer_next_step, text),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
}
