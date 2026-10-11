package com.focusdivergent.app.presentation.launch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusdivergent.app.R
import com.focusdivergent.app.presentation.components.ChoiceCard
import com.focusdivergent.app.presentation.components.PrimaryActionButton
import com.focusdivergent.app.presentation.components.ScreenScaffold
import com.focusdivergent.app.presentation.components.SecondaryActionButton
import com.focusdivergent.app.presentation.components.SectionHeader
import com.focusdivergent.app.presentation.theme.Spacing

/**
 * Preparar la Lanzadera de 2 minutos (RF-3.2): qué se va a empezar, una explicación de lo que va a pasar y un solo
 * botón. Lo que se sugiere ya viene elegido, así que en el caso normal es un toque.
 */
@Composable
fun FocusLaunchScreen(
    onBack: () -> Unit,
    onStarted: () -> Unit,
    onOpenPermissions: () -> Unit,
    viewModel: FocusLaunchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermissions()
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                FocusLaunchEvent.Started -> onStarted()
            }
        }
    }

    FocusLaunchSetup(
        uiState = uiState,
        onBack = onBack,
        onSelect = viewModel::select,
        onStart = viewModel::start,
        onOpenPermissions = onOpenPermissions,
    )
}

@Composable
fun FocusLaunchSetup(
    uiState: FocusLaunchUiState,
    onBack: () -> Unit,
    onSelect: (Long?) -> Unit,
    onStart: () -> Unit,
    onOpenPermissions: () -> Unit,
) {
    ScreenScaffold(title = stringResource(R.string.launch_title), onBack = onBack) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .navigationBarsPadding()
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(text = stringResource(R.string.launch_intro), style = MaterialTheme.typography.bodyLarge)

            if (!uiState.ready) {
                NotReady(onOpenPermissions)
                return@Column
            }

            SectionHeader(stringResource(R.string.launch_task_question))
            uiState.candidates.forEach { task ->
                ChoiceCard(
                    title = task.title,
                    description = "",
                    selected = uiState.selectedId == task.id,
                    onClick = { onSelect(task.id) },
                )
            }
            ChoiceCard(
                title = stringResource(R.string.launch_task_none),
                description = "",
                selected = uiState.selectedId == null,
                onClick = { onSelect(null) },
            )

            PrimaryActionButton(
                text = stringResource(R.string.launch_start),
                onClick = onStart,
                modifier = Modifier.padding(top = Spacing.md),
            )
            Text(
                text = stringResource(R.string.launch_start_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Sin protección activa o sin permiso para mostrarse sobre otras apps, no hay forma de bloquear el móvil. */
@Composable
private fun NotReady(onOpenPermissions: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(text = stringResource(R.string.launch_not_ready_title), style = MaterialTheme.typography.titleMedium)
            Text(text = stringResource(R.string.launch_not_ready_message), style = MaterialTheme.typography.bodyMedium)
        }
    }
    SecondaryActionButton(text = stringResource(R.string.launch_not_ready_action), onClick = onOpenPermissions)
}
