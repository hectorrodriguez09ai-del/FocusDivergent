package com.focusdivergent.app.presentation.chooseforme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusdivergent.app.R
import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.model.Task
import com.focusdivergent.app.presentation.components.EmptyState
import com.focusdivergent.app.presentation.components.EnergyIndicator
import com.focusdivergent.app.presentation.components.EnergySelector
import com.focusdivergent.app.presentation.components.PrimaryActionButton
import com.focusdivergent.app.presentation.components.ScreenScaffold
import com.focusdivergent.app.presentation.components.SecondaryActionButton
import com.focusdivergent.app.presentation.components.labelRes
import com.focusdivergent.app.presentation.tasks.labelRes
import com.focusdivergent.app.presentation.theme.Sizes
import com.focusdivergent.app.presentation.theme.Spacing

/** Tiempos que se ofrecen, en minutos. Nulo es «sin prisa». */
private val TIME_OPTIONS = listOf(5, 15, 30, 60, null)

/**
 * «Elige por mí» (RF-3.3): como mucho dos preguntas, una por pantalla, y **una sola** propuesta. Todo lo que
 * sobra (listas, comparaciones, contadores) se ha quitado a propósito: esto existe para ahorrar decisiones.
 */
@Composable
fun ChooseForMeScreen(
    onBack: () -> Unit,
    onStarted: () -> Unit,
    onSeeList: () -> Unit,
    onCreateTask: () -> Unit,
    viewModel: ChooseForMeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ChooseForMeEvent.Started -> onStarted()
                ChooseForMeEvent.SeeList -> onSeeList()
                ChooseForMeEvent.CreateTask -> onCreateTask()
            }
        }
    }

    ChooseForMeContent(
        uiState = uiState,
        onBack = onBack,
        onEnergy = viewModel::setEnergy,
        onChangeEnergy = viewModel::changeEnergy,
        onTime = viewModel::chooseTime,
        onBackToTime = viewModel::backToTime,
        onStart = viewModel::start,
        onAnother = viewModel::another,
        onSeeList = viewModel::seeList,
        onCreateTask = viewModel::createTask,
    )
}

@Composable
fun ChooseForMeContent(
    uiState: ChooseForMeUiState,
    onBack: () -> Unit,
    onEnergy: (EnergyLevel) -> Unit,
    onChangeEnergy: () -> Unit,
    onTime: (Int?) -> Unit,
    onBackToTime: () -> Unit,
    onStart: () -> Unit,
    onAnother: () -> Unit,
    onSeeList: () -> Unit,
    onCreateTask: () -> Unit,
) {
    ScreenScaffold(title = stringResource(R.string.choose_title), onBack = onBack) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            when (uiState.step) {
                ChooseStep.ENERGY -> EnergyStep(uiState.energy, onEnergy)
                ChooseStep.TIME -> TimeStep(uiState.energy, onTime, onChangeEnergy)
                ChooseStep.RESULT -> uiState.pick?.let { ResultStep(it, uiState.canAskAnother, onStart, onAnother, onSeeList) }
                ChooseStep.NONE_FOUND -> NoneFoundStep(onCreateTask, onBackToTime, onChangeEnergy)
            }
        }
    }
}

@Composable
private fun EnergyStep(selected: EnergyLevel?, onEnergy: (EnergyLevel) -> Unit) {
    Question(stringResource(R.string.choose_energy_question))
    // Tocar un nivel avanza directamente: no hace falta un botón de «Siguiente».
    EnergySelector(selected = selected, onSelect = onEnergy)
}

@Composable
private fun TimeStep(energy: EnergyLevel?, onTime: (Int?) -> Unit, onChangeEnergy: () -> Unit) {
    Question(stringResource(R.string.choose_time_question))
    TIME_OPTIONS.forEach { minutes ->
        SecondaryActionButton(
            text = when (minutes) {
                null -> stringResource(R.string.choose_time_unlimited)
                60 -> stringResource(R.string.choose_time_hour)
                else -> stringResource(R.string.duration_minutes_short, minutes)
            },
            onClick = { onTime(minutes) },
        )
    }
    if (energy != null) {
        TextButton(onClick = onChangeEnergy, modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
            Text(stringResource(R.string.choose_energy_current, stringResource(energy.labelRes)) + " · " + stringResource(R.string.choose_change_energy))
        }
    }
}

@Composable
private fun ResultStep(
    task: Task,
    canAskAnother: Boolean,
    onStart: () -> Unit,
    onAnother: () -> Unit,
    onSeeList: () -> Unit,
) {
    Text(
        text = stringResource(R.string.choose_result_title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(text = task.title, style = MaterialTheme.typography.headlineMedium)

    val meta = buildList {
        add(stringResource(task.type.labelRes))
        task.estimatedMinutes?.let { add(stringResource(R.string.duration_minutes_short, it)) }
    }.joinToString(" · ")
    Text(text = meta, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    EnergyIndicator(task.energyLevel)

    PrimaryActionButton(text = stringResource(R.string.choose_start), onClick = onStart, modifier = Modifier.padding(top = Spacing.md))
    if (canAskAnother) {
        SecondaryActionButton(text = stringResource(R.string.choose_another), onClick = onAnother)
    } else {
        // Sin más cambios, la salida es mirar la lista: ya no se sigue eligiendo por él.
        SecondaryActionButton(text = stringResource(R.string.choose_see_list), onClick = onSeeList)
    }
}

@Composable
private fun NoneFoundStep(onCreateTask: () -> Unit, onBackToTime: () -> Unit, onChangeEnergy: () -> Unit) {
    EmptyState(
        title = stringResource(R.string.choose_none_title),
        message = stringResource(R.string.choose_none_message),
    )
    PrimaryActionButton(text = stringResource(R.string.choose_none_create), onClick = onCreateTask)
    SecondaryActionButton(text = stringResource(R.string.choose_none_retry), onClick = onBackToTime)
    TextButton(
        onClick = onChangeEnergy,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget),
    ) {
        Text(stringResource(R.string.choose_change_energy_only))
    }
}

@Composable
private fun Question(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
    )
}
