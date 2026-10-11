package com.focusdivergent.app.presentation.tasks

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusdivergent.app.R
import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.tasks.TaskProblem
import com.focusdivergent.app.presentation.common.formatShort
import com.focusdivergent.app.presentation.components.ChoiceCard
import com.focusdivergent.app.presentation.components.DurationChips
import com.focusdivergent.app.presentation.components.EnergySelector
import com.focusdivergent.app.presentation.components.PrimaryActionButton
import com.focusdivergent.app.presentation.components.ScreenScaffold
import com.focusdivergent.app.presentation.components.SectionHeader
import com.focusdivergent.app.presentation.theme.Sizes
import com.focusdivergent.app.presentation.theme.Spacing
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.TextStyle

private val DURATION_OPTIONS = listOf(5, 15, 30, 60)

/** Crear o editar una actividad. Solo el título es obligatorio; lo demás tiene un valor razonable de partida. */
@Composable
fun TaskEditorScreen(
    onClose: () -> Unit,
    onStartTimer: (Long) -> Unit = {},
    onOpenSteps: (Long) -> Unit = {},
    viewModel: TaskEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                TaskEditorEvent.Close -> onClose()
                is TaskEditorEvent.OpenSteps -> onOpenSteps(event.taskId)
            }
        }
    }

    TaskEditorContent(
        uiState = uiState,
        onBack = onClose,
        onTitle = viewModel::setTitle,
        onType = viewModel::setType,
        onEnergy = viewModel::setEnergy,
        onDate = viewModel::setDate,
        onTime = viewModel::setTime,
        onDuration = viewModel::setEstimatedMinutes,
        onToggleDetails = viewModel::toggleDetails,
        onWeekly = viewModel::setWeekly,
        onToggleWeekDay = viewModel::toggleWeekDay,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onStartTimer = onStartTimer,
        onSteps = viewModel::openSteps,
    )
}

@Composable
fun TaskEditorContent(
    uiState: TaskEditorUiState,
    onBack: () -> Unit,
    onTitle: (String) -> Unit,
    onType: (TaskType) -> Unit,
    onEnergy: (EnergyLevel) -> Unit,
    onDate: (LocalDate?) -> Unit,
    onTime: (LocalTime?) -> Unit,
    onDuration: (Int?) -> Unit,
    onToggleDetails: () -> Unit,
    onWeekly: (Boolean) -> Unit,
    onToggleWeekDay: (DayOfWeek) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onStartTimer: (Long) -> Unit = {},
    onSteps: () -> Unit = {},
) {
    val draft = uiState.draft
    val titleFocus = remember { FocusRequester() }

    // En una actividad nueva el cursor va directo al título: se puede empezar a escribir sin tocar nada.
    LaunchedEffect(uiState.loading) {
        if (!uiState.loading && draft.isNew) titleFocus.requestFocus()
    }

    ScreenScaffold(
        title = stringResource(if (draft.isNew) R.string.task_editor_new else R.string.task_editor_edit),
        onBack = onBack,
        actions = {
            if (!draft.isNew) {
                // Texto y no icono de papelera, y en el color neutro: borrar no es una alarma (RNF-3).
                TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
            }
        },
        bottomBar = {
            PrimaryActionButton(
                text = stringResource(R.string.action_save),
                onClick = onSave,
                enabled = !uiState.loading,
                modifier = Modifier
                    // Con el teclado abierto, «Guardar» sube con él y sigue a la vista.
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
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            val titleMissing = TaskProblem.TITLE_BLANK in uiState.problems
            OutlinedTextField(
                value = draft.title,
                onValueChange = onTitle,
                label = { Text(stringResource(R.string.task_editor_title_label)) },
                isError = titleMissing,
                supportingText = if (titleMissing) {
                    { Text(stringResource(R.string.task_editor_title_missing)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(titleFocus),
            )

            // Solo una actividad ya guardada puede acompañar a un temporizador: así la sesión sabe a cuál se refiere.
            draft.original?.let { original ->
                TextButton(onClick = { onStartTimer(original.id) }, modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
                    Text(stringResource(R.string.task_editor_timer))
                }
            }

            // Las rutinas se marcan por día y no se parten en pasos; una tarea o una Obligación sí.
            if (draft.type != TaskType.ROUTINE) {
                TextButton(onClick = onSteps, modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
                    Text(
                        if (uiState.steps.hasSteps) {
                            stringResource(R.string.steps_button_progress, uiState.steps.done, uiState.steps.total)
                        } else {
                            stringResource(R.string.steps_button)
                        },
                    )
                }
            }

            SectionHeader(stringResource(R.string.task_editor_type))
            TaskType.entries.forEach { type ->
                ChoiceCard(
                    title = stringResource(type.labelRes),
                    description = stringResource(type.descriptionRes),
                    selected = draft.type == type,
                    onClick = { onType(type) },
                )
            }

            SectionHeader(stringResource(R.string.task_editor_energy))
            EnergySelector(selected = draft.energy, onSelect = onEnergy)

            when (draft.type) {
                TaskType.ROUTINE -> {
                    SectionHeader(stringResource(R.string.task_editor_repeat))
                    RepeatFields(uiState, onWeekly, onToggleWeekDay)
                    SectionHeader(stringResource(R.string.task_editor_time_optional))
                    TimeField(draft.time, onTime)
                    Text(
                        text = stringResource(R.string.task_editor_routine_time_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SectionHeader(stringResource(R.string.task_editor_duration))
                    DurationChips(DURATION_OPTIONS, draft.estimatedMinutes, onDuration)
                }

                TaskType.OBLIGATION -> {
                    SectionHeader(stringResource(R.string.task_editor_when))
                    DateField(draft.date, uiState.today, onDate)
                    TimeField(draft.time, onTime)
                    if (TaskProblem.OBLIGATION_NEEDS_DATE_AND_TIME in uiState.problems) {
                        // Aviso en ámbar, junto al campo y diciendo qué hacer; no un error en rojo.
                        Text(
                            text = stringResource(R.string.task_editor_obligation_needs_time),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    SectionHeader(stringResource(R.string.task_editor_duration))
                    DurationChips(DURATION_OPTIONS, draft.estimatedMinutes, onDuration)
                }

                TaskType.TASK -> {
                    // Una tarea vale con el título. El resto está a un toque, pero no estorba.
                    TextButton(onClick = onToggleDetails, modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
                        Text(
                            stringResource(
                                if (uiState.detailsExpanded) R.string.task_editor_details_hide else R.string.task_editor_details_show,
                            ),
                        )
                    }
                    if (uiState.detailsExpanded) {
                        SectionHeader(stringResource(R.string.task_editor_when))
                        DateField(draft.date, uiState.today, onDate)
                        TimeField(draft.time, onTime)
                        SectionHeader(stringResource(R.string.task_editor_duration))
                        DurationChips(DURATION_OPTIONS, draft.estimatedMinutes, onDuration)
                    }
                }
            }
        }
    }
}

/** Día: «Hoy» y «Mañana» a un toque, y un calendario para cualquier otro. Tocar el día elegido lo quita. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(date: LocalDate?, today: LocalDate, onDate: (LocalDate?) -> Unit) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val tomorrow = today.plusDays(1)
    val isOtherDay = date != null && date != today && date != tomorrow

    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        DateChip(stringResource(R.string.date_today), date == today) { onDate(today.takeUnless { date == today }) }
        DateChip(stringResource(R.string.date_tomorrow), date == tomorrow) { onDate(tomorrow.takeUnless { date == tomorrow }) }
        DateChip(
            label = if (isOtherDay) date.formatShort() else stringResource(R.string.date_other),
            selected = isOtherDay,
            onClick = { showPicker = true },
        )
    }

    if (showPicker) {
        val state = rememberDatePickerState(
            // El selector trabaja en UTC: se le da y se le pide el día como medianoche UTC.
            initialSelectedDateMillis = (date ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                        showPicker = false
                    },
                ) { Text(stringResource(R.string.action_accept)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

/** Hora: un botón que abre el reloj. Con una hora puesta, otro para quitarla. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(time: LocalTime?, onTime: (LocalTime?) -> Unit) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        DateChip(
            label = time?.formatShort() ?: stringResource(R.string.time_choose),
            selected = time != null,
            onClick = { showPicker = true },
        )
        if (time != null) {
            TextButton(onClick = { onTime(null) }, modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
                Text(stringResource(R.string.time_clear))
            }
        }
    }

    if (showPicker) {
        val state = rememberTimePickerState(
            initialHour = time?.hour ?: 9,
            initialMinute = time?.minute ?: 0,
            is24Hour = DateFormat.is24HourFormat(context),
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onTime(LocalTime.of(state.hour, state.minute))
                        showPicker = false
                    },
                ) { Text(stringResource(R.string.action_accept)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
            text = { TimePicker(state = state) },
        )
    }
}

/** Repetición de una rutina: todos los días, o solo los que se marquen. */
@Composable
private fun RepeatFields(
    uiState: TaskEditorUiState,
    onWeekly: (Boolean) -> Unit,
    onToggleWeekDay: (DayOfWeek) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        DateChip(stringResource(R.string.repeat_daily), !uiState.weekly) { onWeekly(false) }
        DateChip(stringResource(R.string.repeat_some_days), uiState.weekly) { onWeekly(true) }
    }
    if (uiState.weekly) {
        // Idioma de la configuración (observable), no Locale.getDefault(): así se actualiza si cambia el idioma.
        val locale = LocalConfiguration.current.locales[0]
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            DayOfWeek.entries.forEach { day ->
                DateChip(
                    label = day.getDisplayName(TextStyle.SHORT, locale).replaceFirstChar { it.titlecase(locale) },
                    selected = day in uiState.weekDays,
                    onClick = { onToggleWeekDay(day) },
                )
            }
        }
        if (uiState.weekDays.isEmpty()) {
            Text(
                text = stringResource(R.string.repeat_no_days_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DateChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.heightIn(min = Sizes.touchTarget),
    )
}

@get:StringRes
private val TaskType.descriptionRes: Int
    get() = when (this) {
        TaskType.TASK -> R.string.task_type_task_description
        TaskType.OBLIGATION -> R.string.task_type_obligation_description
        TaskType.ROUTINE -> R.string.task_type_routine_description
    }
