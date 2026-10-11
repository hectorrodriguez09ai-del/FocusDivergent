package com.focusdivergent.app.presentation.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusdivergent.app.R
import com.focusdivergent.app.presentation.components.PrimaryActionButton
import com.focusdivergent.app.presentation.components.ScreenScaffold
import com.focusdivergent.app.presentation.components.SecondaryActionButton
import com.focusdivergent.app.presentation.theme.Spacing

/**
 * Los pasos de una tarea de uno en uno, a pantalla completa (RF-3.1): se ve un solo paso, sin lista, sin barras de
 * progreso que midan lo que falta y con «Hecho» y «Ahora no» del mismo peso, para que apartar un paso cueste lo mismo
 * que hacerlo y no se sienta como un fallo.
 */
@Composable
fun StepFocusScreen(
    onBack: () -> Unit,
    onEditSteps: () -> Unit,
    viewModel: StepFocusViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                StepFocusEvent.Close -> onBack()
            }
        }
    }

    StepFocusContent(
        uiState = uiState,
        onBack = onBack,
        onDone = viewModel::done,
        onNotNow = viewModel::notNow,
        onReview = viewModel::reviewAgain,
        onFinishTask = viewModel::finishTask,
        onEditSteps = onEditSteps,
    )
}

@Composable
fun StepFocusContent(
    uiState: StepFocusUiState,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onNotNow: () -> Unit,
    onReview: () -> Unit,
    onFinishTask: () -> Unit,
    onEditSteps: () -> Unit,
) {
    ScreenScaffold(
        title = stringResource(R.string.step_focus_title),
        onBack = onBack,
        bottomBar = {
            Column(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = Spacing.screen, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                when (uiState) {
                    is StepFocusUiState.Current -> {
                        // Mismo componente, mismo tamaño y mismo estilo: ninguna de las dos opciones pesa más.
                        SecondaryActionButton(text = stringResource(R.string.step_focus_done), onClick = onDone)
                        SecondaryActionButton(text = stringResource(R.string.step_focus_not_now), onClick = onNotNow)
                    }

                    is StepFocusUiState.AllDone -> {
                        if (uiState.canFinishTask) {
                            PrimaryActionButton(text = stringResource(R.string.step_focus_mark_task_done), onClick = onFinishTask)
                            SecondaryActionButton(text = stringResource(R.string.step_focus_back), onClick = onBack)
                        } else {
                            PrimaryActionButton(text = stringResource(R.string.step_focus_back), onClick = onBack)
                        }
                    }

                    is StepFocusUiState.Paused -> {
                        SecondaryActionButton(text = stringResource(R.string.step_focus_review), onClick = onReview)
                        SecondaryActionButton(text = stringResource(R.string.step_focus_back), onClick = onBack)
                    }

                    is StepFocusUiState.NoSteps -> {
                        PrimaryActionButton(text = stringResource(R.string.step_focus_no_steps_action), onClick = onEditSteps)
                    }

                    StepFocusUiState.Loading -> Unit
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
        ) {
            when (uiState) {
                is StepFocusUiState.Current -> {
                    Text(
                        text = stringResource(R.string.step_focus_counter, uiState.number, uiState.total),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = uiState.step.text,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.step_focus_duration_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (uiState.taskTitle.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.steps_editor_for, uiState.taskTitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                is StepFocusUiState.AllDone -> Message(
                    title = stringResource(R.string.step_focus_all_done_title),
                    message = stringResource(R.string.step_focus_all_done_message),
                )

                is StepFocusUiState.Paused -> Message(
                    title = stringResource(R.string.step_focus_paused_title),
                    message = stringResource(R.string.step_focus_paused_message),
                )

                is StepFocusUiState.NoSteps -> Message(
                    title = stringResource(R.string.step_focus_no_steps_title),
                    message = stringResource(R.string.step_focus_no_steps_message),
                )

                StepFocusUiState.Loading -> Unit
            }
        }
    }
}

@Composable
private fun Message(title: String, message: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
