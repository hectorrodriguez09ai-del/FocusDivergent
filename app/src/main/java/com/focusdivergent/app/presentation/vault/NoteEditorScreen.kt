package com.focusdivergent.app.presentation.vault

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusdivergent.app.R
import com.focusdivergent.app.presentation.components.ScreenScaffold
import com.focusdivergent.app.presentation.components.SectionHeader
import com.focusdivergent.app.presentation.markdown.MarkdownText
import com.focusdivergent.app.presentation.theme.Sizes
import com.focusdivergent.app.presentation.theme.Spacing

/**
 * Crear o editar una nota de la Bóveda. No hay botón «Guardar»: se guarda sola, al dejar de escribir, al cambiar de
 * app y al salir. «Atrás» espera a que esté guardada antes de cerrar.
 */
@Composable
fun NoteEditorScreen(
    onClose: () -> Unit,
    viewModel: NoteEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                NoteEditorEvent.Close -> onClose()
            }
        }
    }
    // Al irse a otra app o apagar la pantalla, lo escrito queda guardado aunque el sistema acabe con el proceso.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flush() }
    BackHandler(onBack = viewModel::saveAndClose)

    NoteEditorContent(
        uiState = uiState,
        onBack = viewModel::saveAndClose,
        onTitle = viewModel::setTitle,
        onBody = viewModel::setBody,
        onPreview = viewModel::setPreview,
        onTagInput = viewModel::setTagInput,
        onAddTag = viewModel::addTag,
        onSuggestedTag = viewModel::addSuggestedTag,
        onRemoveTag = viewModel::removeTag,
        onMove = viewModel::moveTo,
        onDelete = viewModel::delete,
    )
}

@Composable
fun NoteEditorContent(
    uiState: NoteEditorUiState,
    onBack: () -> Unit,
    onTitle: (String) -> Unit,
    onBody: (String) -> Unit,
    onPreview: (Boolean) -> Unit,
    onTagInput: (String) -> Unit,
    onAddTag: () -> Unit,
    onSuggestedTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onMove: (Long?) -> Unit,
    onDelete: () -> Unit,
) {
    var moveOpen by rememberSaveable { mutableStateOf(false) }
    val titleFocus = remember { FocusRequester() }
    // Una nota nueva arranca con el cursor en el título; una existente no abre el teclado sin que se pida.
    val isBlankNote = uiState.title.isEmpty() && uiState.body.isEmpty()
    LaunchedEffect(uiState.loading) {
        if (!uiState.loading && isBlankNote) titleFocus.requestFocus()
    }

    ScreenScaffold(
        title = stringResource(R.string.note_editor_title),
        onBack = onBack,
        actions = {
            TextButton(onClick = { onPreview(!uiState.preview) }, modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
                Text(
                    text = stringResource(if (uiState.preview) R.string.note_editor_edit else R.string.note_editor_preview),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // El teclado no tapa lo que se escribe: el contenido se desplaza y deja sitio.
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            OutlinedTextField(
                value = uiState.title,
                onValueChange = onTitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(titleFocus),
                label = { Text(stringResource(R.string.note_editor_title_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            )

            if (uiState.preview) {
                if (uiState.body.isBlank()) {
                    Text(
                        text = stringResource(R.string.note_editor_preview_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    MarkdownText(markdown = uiState.body)
                }
            } else {
                OutlinedTextField(
                    value = uiState.body,
                    onValueChange = onBody,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.note_editor_body_label)) },
                    supportingText = { Text(stringResource(R.string.note_editor_body_hint)) },
                    minLines = 8,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }

            TagsSection(
                tags = uiState.tags,
                input = uiState.tagInput,
                suggestions = uiState.suggestions,
                onInput = onTagInput,
                onAdd = onAddTag,
                onSuggested = onSuggestedTag,
                onRemove = onRemoveTag,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        R.string.note_editor_location,
                        uiState.folderLabel.ifEmpty { stringResource(R.string.nav_vault) },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { moveOpen = true }, modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
                    Text(stringResource(R.string.note_editor_move), style = MaterialTheme.typography.labelLarge)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(if (uiState.saved) R.string.note_editor_saved else R.string.note_editor_autosave),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
                    Text(stringResource(R.string.note_editor_delete), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    if (moveOpen) {
        MoveDialog(
            folders = uiState.folders,
            selected = uiState.parentId,
            onSelect = {
                onMove(it)
                moveOpen = false
            },
            onDismiss = { moveOpen = false },
        )
    }
}

@Composable
private fun TagsSection(
    tags: List<String>,
    input: String,
    suggestions: List<String>,
    onInput: (String) -> Unit,
    onAdd: () -> Unit,
    onSuggested: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(text = stringResource(R.string.note_editor_tags))
        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                tags.forEach { tag ->
                    val remove = stringResource(R.string.note_editor_tag_remove, tag)
                    InputChip(
                        selected = false,
                        onClick = { onRemove(tag) },
                        label = { Text("#$tag") },
                        trailingIcon = {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        modifier = Modifier
                            .heightIn(min = Sizes.touchTarget)
                            .semantics { contentDescription = remove },
                    )
                }
            }
        }
        OutlinedTextField(
            value = input,
            onValueChange = onInput,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.note_editor_tag_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAdd() }),
        )
        if (suggestions.isNotEmpty()) {
            Text(
                text = stringResource(R.string.note_editor_tag_suggested),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                suggestions.forEach { tag ->
                    SuggestionChip(
                        onClick = { onSuggested(tag) },
                        label = { Text("#$tag") },
                        modifier = Modifier.heightIn(min = Sizes.touchTarget),
                    )
                }
            }
        }
    }
}

@Composable
private fun MoveDialog(
    folders: List<FolderOption>,
    selected: Long?,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.note_editor_move_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                folders.forEach { option ->
                    val label = option.label.ifEmpty { stringResource(R.string.note_editor_move_root) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Sizes.touchTarget)
                            .selectable(selected = option.id == selected, role = Role.RadioButton, onClick = { onSelect(option.id) }),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        RadioButton(selected = option.id == selected, onClick = null)
                        Text(text = label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
