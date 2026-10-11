package com.focusdivergent.app.presentation.steps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusdivergent.app.R
import com.focusdivergent.app.domain.steps.StepRules
import com.focusdivergent.app.domain.steps.StepTemplate
import com.focusdivergent.app.domain.steps.TaskStep
import com.focusdivergent.app.presentation.components.EmptyState
import com.focusdivergent.app.presentation.components.PrimaryActionButton
import com.focusdivergent.app.presentation.components.ScreenScaffold
import com.focusdivergent.app.presentation.components.SecondaryActionButton
import com.focusdivergent.app.presentation.components.SectionHeader
import com.focusdivergent.app.presentation.theme.Sizes
import com.focusdivergent.app.presentation.theme.Spacing

/** Preparar los pasos de una tarea: escribirlos, ordenarlos o partir de una plantilla, y empezar con el primero. */
@Composable
fun StepsEditorScreen(
    taskId: Long,
    onBack: () -> Unit,
    onStart: (Long) -> Unit,
    viewModel: StepsEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    StepsEditorContent(
        uiState = uiState,
        builtInTemplates = rememberBuiltInStepTemplates(),
        onBack = onBack,
        onStart = { onStart(taskId) },
        onAdd = viewModel::add,
        onRename = viewModel::rename,
        onDelete = viewModel::delete,
        onMove = viewModel::move,
        onSetDone = viewModel::setDone,
        onApplyTemplate = viewModel::applyTemplate,
        onSaveTemplate = viewModel::saveAsTemplate,
        onDeleteTemplate = viewModel::deleteTemplate,
        onDismissNotice = viewModel::dismissNotice,
        onAskAi = viewModel::askAi,
        onAcceptAiConsent = viewModel::acceptAiConsent,
        onAddSuggested = viewModel::addSuggested,
        onDismissAi = viewModel::dismissAi,
    )
}

@Composable
fun StepsEditorContent(
    uiState: StepsEditorUiState,
    builtInTemplates: List<StepTemplate>,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onAdd: (String) -> Unit,
    onRename: (TaskStep, String) -> Unit,
    onDelete: (TaskStep) -> Unit,
    onMove: (TaskStep, up: Boolean) -> Unit,
    onSetDone: (TaskStep, Boolean) -> Unit,
    onApplyTemplate: (StepTemplate) -> Unit,
    onSaveTemplate: (String) -> Unit,
    onDeleteTemplate: (StepTemplate) -> Unit,
    onDismissNotice: () -> Unit,
    onAskAi: () -> Unit = {},
    onAcceptAiConsent: () -> Unit = {},
    onAddSuggested: (List<String>) -> Unit = {},
    onDismissAi: () -> Unit = {},
) {
    var newStep by rememberSaveable { mutableStateOf("") }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showNameDialog by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }

    ScreenScaffold(
        title = stringResource(R.string.steps_editor_title),
        onBack = onBack,
        bottomBar = {
            PrimaryActionButton(
                text = stringResource(R.string.steps_start),
                onClick = onStart,
                enabled = uiState.steps.any { !it.done },
                modifier = Modifier
                    // Con el teclado abierto, el botón sube con él y sigue a la vista.
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                    .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            uiState.taskTitle?.let {
                Text(
                    text = stringResource(R.string.steps_editor_for, it),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = stringResource(R.string.steps_editor_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (uiState.steps.isEmpty() && !uiState.loading) {
                EmptyState(
                    title = stringResource(R.string.steps_empty_title),
                    message = stringResource(R.string.steps_empty_message),
                )
            }

            uiState.steps.forEachIndexed { index, step ->
                StepRow(
                    step = step,
                    isFirst = index == 0,
                    isLast = index == uiState.steps.lastIndex,
                    onToggle = { onSetDone(step, it) },
                    onEdit = { editing = step.id },
                    onMove = { up -> onMove(step, up) },
                    onDelete = { onDelete(step) },
                )
            }

            if (uiState.canAddMore) {
                val submit = {
                    if (newStep.isNotBlank()) {
                        onAdd(newStep)
                        newStep = ""
                    }
                }
                OutlinedTextField(
                    value = newStep,
                    onValueChange = { newStep = it },
                    label = { Text(stringResource(R.string.steps_add_label)) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                SecondaryActionButton(
                    text = stringResource(R.string.steps_add_action),
                    onClick = submit,
                    enabled = newStep.isNotBlank(),
                )
            } else {
                Text(
                    text = stringResource(R.string.steps_limit_reached, StepRules.MAX_STEPS_PER_TASK),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            uiState.notice?.let { notice ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = noticeText(notice),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismissNotice, modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
                        Text(stringResource(R.string.action_accept))
                    }
                }
            }

            SectionHeader(stringResource(R.string.steps_templates_header))
            SecondaryActionButton(text = stringResource(R.string.steps_template_use), onClick = { showPicker = true })
            if (uiState.steps.isNotEmpty()) {
                TextButton(onClick = { showNameDialog = true }, modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
                    Text(stringResource(R.string.steps_template_save))
                }
            }

            // Solo existe si el usuario puso su propia clave: sin ella no se ofrece nada que salga a internet.
            if (uiState.ai.available) {
                SectionHeader(stringResource(R.string.ai_suggest_header))
                AiSuggestion(uiState.ai.phase, onAsk = onAskAi, onAdd = onAddSuggested, onDismiss = onDismissAi)
            }
        }
    }

    if (uiState.ai.phase == AiPhase.AskingConsent) {
        AlertDialog(
            onDismissRequest = onDismissAi,
            title = { Text(stringResource(R.string.ai_consent_title)) },
            text = { AiConsentContent(host = uiState.ai.host.orEmpty(), taskTitle = uiState.taskTitle.orEmpty()) },
            confirmButton = {
                TextButton(onClick = onAcceptAiConsent) { Text(stringResource(R.string.ai_consent_accept)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissAi) { Text(stringResource(R.string.ai_consent_decline)) }
            },
        )
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(R.string.steps_template_picker_title)) },
            text = {
                TemplatePickerContent(
                    builtIn = builtInTemplates,
                    mine = uiState.userTemplates,
                    onPick = {
                        onApplyTemplate(it)
                        showPicker = false
                    },
                    onDelete = onDeleteTemplate,
                )
            },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showNameDialog) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text(stringResource(R.string.steps_template_name_title)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.steps_template_name_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSaveTemplate(name)
                        showNameDialog = false
                    },
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    editing?.let { id ->
        uiState.steps.firstOrNull { it.id == id }?.let { step ->
            var text by rememberSaveable(id) { mutableStateOf(step.text) }
            AlertDialog(
                onDismissRequest = { editing = null },
                title = { Text(stringResource(R.string.steps_edit_title)) },
                text = {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        maxLines = 4,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onRename(step, text)
                            editing = null
                        },
                    ) { Text(stringResource(R.string.action_save)) }
                },
                dismissButton = {
                    TextButton(onClick = { editing = null }) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        } ?: run { editing = null }
    }
}

/** El contenido del selector de plantillas: las de partida y las propias, cada una con cuántos pasos tiene. */
@Composable
fun TemplatePickerContent(
    builtIn: List<StepTemplate>,
    mine: List<StepTemplate>,
    onPick: (StepTemplate) -> Unit,
    onDelete: (StepTemplate) -> Unit,
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        SectionHeader(stringResource(R.string.steps_template_builtin))
        builtIn.forEach { TemplateRow(it, onPick = { onPick(it) }, onDelete = null) }

        SectionHeader(stringResource(R.string.steps_template_mine))
        if (mine.isEmpty()) {
            Text(
                text = stringResource(R.string.steps_template_none_mine),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        mine.forEach { TemplateRow(it, onPick = { onPick(it) }, onDelete = { onDelete(it) }) }
    }
}

@Composable
private fun TemplateRow(template: StepTemplate, onPick: () -> Unit, onDelete: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = onPick,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = Sizes.touchTarget),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.small,
        ) {
            Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                Text(text = template.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = pluralStringResource(R.plurals.steps_template_count, template.steps.size, template.steps.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onDelete != null) {
            val label = stringResource(R.string.steps_template_delete, template.name)
            IconButton(onClick = onDelete, modifier = Modifier.semantics { contentDescription = label }) {
                Icon(painter = painterResource(R.drawable.ic_close), contentDescription = null)
            }
        }
    }
}

/** Un paso de la lista: casilla para marcarlo, el texto (tocarlo lo edita) y un menú para ordenarlo o quitarlo. */
@Composable
private fun StepRow(
    step: TaskStep,
    isFirst: Boolean,
    isLast: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onMove: (up: Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by rememberSaveable(step.id) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val toggleLabel = stringResource(R.string.steps_done_toggle, step.text)
            Checkbox(
                checked = step.done,
                onCheckedChange = onToggle,
                modifier = Modifier.semantics { contentDescription = toggleLabel },
            )
            Text(
                text = step.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (step.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (step.done) TextDecoration.LineThrough else null,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Sizes.touchTarget)
                    .clickable(onClick = onEdit)
                    .padding(vertical = Spacing.sm),
            )
            val moreLabel = stringResource(R.string.steps_more, step.text)
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.semantics { contentDescription = moreLabel }) {
                Icon(painter = painterResource(R.drawable.ic_more), contentDescription = null)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.steps_menu_edit)) },
                    onClick = { menuOpen = false; onEdit() },
                )
                if (!isFirst) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.steps_menu_up)) },
                        onClick = { menuOpen = false; onMove(true) },
                    )
                }
                if (!isLast) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.steps_menu_down)) },
                        onClick = { menuOpen = false; onMove(false) },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.steps_menu_delete)) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun noticeText(notice: StepsNotice): String = when (notice) {
    is StepsNotice.TemplateSaved -> stringResource(R.string.steps_template_saved, notice.name)
    StepsNotice.TemplateNeedsName -> stringResource(R.string.steps_template_needs_name)
    StepsNotice.TemplateNeedsSteps -> stringResource(R.string.steps_template_needs_steps)
    StepsNotice.TemplateLimit -> stringResource(R.string.steps_template_too_many)
    StepsNotice.TemplateApplied -> stringResource(R.string.steps_template_applied)
    is StepsNotice.StepLimit -> stringResource(R.string.steps_limit_reached, notice.max)
}

/** Exactamente qué se envía y a quién, antes de enviarlo. */
@Composable
fun AiConsentContent(host: String, taskTitle: String) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(text = stringResource(R.string.ai_consent_body, host), style = MaterialTheme.typography.bodyMedium)
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.small) {
            Text(
                text = stringResource(R.string.ai_consent_payload, taskTitle),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
            )
        }
        Text(
            text = stringResource(R.string.ai_consent_notes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AiSuggestion(
    phase: AiPhase,
    onAsk: () -> Unit,
    onAdd: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    when (phase) {
        AiPhase.Idle, AiPhase.AskingConsent -> SecondaryActionButton(text = stringResource(R.string.ai_suggest_button), onClick = onAsk)

        AiPhase.Loading -> Text(
            text = stringResource(R.string.ai_suggest_loading),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        is AiPhase.Review -> AiReview(phase.steps, onAdd = onAdd, onDiscard = onDismiss)

        is AiPhase.Failed -> {
            Text(
                text = stringResource(
                    when (phase.reason) {
                        AiFailure.NO_CONNECTION -> R.string.ai_failed_no_connection
                        AiFailure.REJECTED -> R.string.ai_failed_rejected
                        AiFailure.UNREADABLE -> R.string.ai_failed_unreadable
                        AiFailure.NOT_READY -> R.string.ai_failed_not_ready
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            SecondaryActionButton(text = stringResource(R.string.ai_suggest_button), onClick = onAsk)
        }
    }
}

/** La propuesta de la IA, para revisarla: cada paso con su casilla (todos marcados) antes de añadir nada. */
@Composable
fun AiReview(steps: List<String>, onAdd: (List<String>) -> Unit, onDiscard: () -> Unit) {
    var kept by rememberSaveable(steps) { mutableStateOf(steps.indices.toSet()) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(text = stringResource(R.string.ai_review_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.ai_review_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        steps.forEachIndexed { index, text ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val label = stringResource(R.string.ai_review_item, text)
                    Checkbox(
                        checked = index in kept,
                        onCheckedChange = { on -> kept = if (on) kept + index else kept - index },
                        modifier = Modifier.semantics { contentDescription = label },
                    )
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = Spacing.sm, horizontal = Spacing.xs),
                    )
                }
            }
        }
        PrimaryActionButton(
            text = stringResource(R.string.ai_review_add),
            onClick = { onAdd(steps.filterIndexed { index, _ -> index in kept }) },
            enabled = kept.isNotEmpty(),
        )
        SecondaryActionButton(text = stringResource(R.string.ai_review_discard), onClick = onDiscard)
    }
}
