package com.focusdivergent.app.presentation.vault

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusdivergent.app.R
import com.focusdivergent.app.domain.braindump.IdeaText
import com.focusdivergent.app.domain.model.BrainDump
import com.focusdivergent.app.domain.model.VaultNode
import com.focusdivergent.app.presentation.capture.CaptureActivity
import com.focusdivergent.app.presentation.capture.requestAddCaptureTile
import com.focusdivergent.app.presentation.common.formatShort
import com.focusdivergent.app.presentation.components.EmptyState
import com.focusdivergent.app.presentation.components.FocusCard
import com.focusdivergent.app.presentation.components.LinkCard
import com.focusdivergent.app.presentation.components.PrimaryActionButton
import com.focusdivergent.app.presentation.components.ScreenScaffold
import com.focusdivergent.app.presentation.components.SectionHeader
import com.focusdivergent.app.presentation.theme.Sizes
import com.focusdivergent.app.presentation.theme.Spacing
import java.time.Instant
import java.time.ZoneId

/**
 * Pestaña «Bóveda» (RF-5.2): carpetas y notas en Markdown, búsqueda de texto completo y la bandeja de entrada del
 * Brain Dump (RF-5.1). La bandeja y la captura rápida solo se ven en la raíz; dentro de una carpeta, solo su contenido.
 */
@Composable
fun VaultScreen(
    onMakeTask: (BrainDump) -> Unit,
    onOpenNote: (Long) -> Unit,
    onNewNote: (parentId: Long?) -> Unit,
    viewModel: VaultViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val notice = uiState.notice
    val discardedMessage = stringResource(R.string.idea_discarded)
    val savedMessage = stringResource(R.string.idea_saved_to_vault)
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(notice) {
        when (notice) {
            null -> Unit
            is VaultNotice.Discarded -> {
                val result = snackbarHostState.showSnackbar(
                    message = discardedMessage,
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.undoDiscard(notice.idea) else viewModel.dismissNotice(notice)
            }
            is VaultNotice.SavedToVault -> {
                snackbarHostState.showSnackbar(message = savedMessage, duration = SnackbarDuration.Short)
                viewModel.dismissNotice(notice)
            }
        }
    }

    // Borrar una nota no pide confirmación (se borra en su editor); a cambio, aquí se puede deshacer unos segundos.
    val deletedNote = uiState.deletedNote
    val deletedMessage = if (deletedNote == null || deletedNote.title.isBlank()) {
        stringResource(R.string.vault_note_deleted_untitled)
    } else {
        stringResource(R.string.vault_note_deleted, deletedNote.title)
    }
    LaunchedEffect(deletedNote) {
        if (deletedNote == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = deletedMessage,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDeleteNote() else viewModel.dismissDeletedNote(deletedNote)
    }

    // Atrás sale primero de la búsqueda y luego sube de carpeta; en la raíz, vuelve a «Hoy» como cualquier pestaña.
    BackHandler(enabled = uiState.isSearching || uiState.folderId != null, onBack = viewModel::up)

    // Añadir el mosaico con un toque solo existe desde Android 13; antes se hace a mano desde Ajustes rápidos.
    val onAddTile: (() -> Unit)? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        { requestAddCaptureTile(context, onHandled = viewModel::tileOfferHandled) }
    } else {
        null
    }

    VaultContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onCapture = { context.startActivity(CaptureActivity.intent(context)) },
        onMakeTask = onMakeTask,
        onSaveToVault = viewModel::saveToVault,
        onDiscard = viewModel::discard,
        onAddTile = onAddTile,
        onDismissTileOffer = viewModel::tileOfferHandled,
        onQuery = viewModel::setQuery,
        onUp = viewModel::up,
        onOpenFolder = viewModel::openFolder,
        onOpenNote = onOpenNote,
        onNewNote = { onNewNote(uiState.folderId) },
        onCreateFolder = viewModel::createFolder,
        onRenameFolder = viewModel::renameCurrentFolder,
        onDeleteFolder = viewModel::deleteCurrentFolder,
    )
}

/** Diálogo abierto en la pantalla. Es una enumeración para que sobreviva a un giro de pantalla. */
private enum class VaultDialog { NONE, NEW_FOLDER, RENAME_FOLDER, DELETE_FOLDER }

@Composable
fun VaultContent(
    uiState: VaultUiState,
    onCapture: () -> Unit,
    onMakeTask: (BrainDump) -> Unit,
    onSaveToVault: (BrainDump) -> Unit,
    onDiscard: (BrainDump) -> Unit,
    onAddTile: (() -> Unit)?,
    onDismissTileOffer: () -> Unit,
    onQuery: (String) -> Unit,
    onUp: () -> Unit,
    onOpenFolder: (Long) -> Unit,
    onOpenNote: (Long) -> Unit,
    onNewNote: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameFolder: (String) -> Unit,
    onDeleteFolder: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var dialog by rememberSaveable { mutableStateOf(VaultDialog.NONE) }
    var menuOpen by remember { mutableStateOf(false) }
    val folder = uiState.currentFolder
    val atRoot = uiState.folderId == null

    ScreenScaffold(
        title = folder?.title ?: stringResource(R.string.nav_vault),
        onBack = if (atRoot) null else onUp,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        actions = {
            if (folder != null) {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(painterResource(R.drawable.ic_more), contentDescription = stringResource(R.string.vault_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.vault_folder_rename)) },
                        onClick = {
                            menuOpen = false
                            dialog = VaultDialog.RENAME_FOLDER
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.vault_folder_delete)) },
                        onClick = {
                            menuOpen = false
                            dialog = VaultDialog.DELETE_FOLDER
                        },
                    )
                }
            }
        },
        floatingActionButton = {
            if (!uiState.isSearching) {
                ExtendedFloatingActionButton(
                    onClick = onNewNote,
                    icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                    text = { Text(stringResource(R.string.vault_new_note)) },
                )
            }
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
            item(key = "search") {
                SearchField(query = uiState.query, onQuery = onQuery)
            }

            if (uiState.isSearching) {
                searchResults(uiState, onOpenFolder = onOpenFolder, onOpenNote = onOpenNote)
            } else {
                if (atRoot) {
                    inbox(
                        uiState = uiState,
                        onCapture = onCapture,
                        onMakeTask = onMakeTask,
                        onSaveToVault = onSaveToVault,
                        onDiscard = onDiscard,
                        onAddTile = onAddTile,
                        onDismissTileOffer = onDismissTileOffer,
                    )
                }
                contents(uiState, onNewFolder = { dialog = VaultDialog.NEW_FOLDER }, onOpenFolder = onOpenFolder, onOpenNote = onOpenNote)
            }
        }
    }

    when (dialog) {
        VaultDialog.NONE -> Unit
        VaultDialog.NEW_FOLDER -> FolderNameDialog(
            title = stringResource(R.string.vault_new_folder),
            initial = "",
            onConfirm = {
                onCreateFolder(it)
                dialog = VaultDialog.NONE
            },
            onDismiss = { dialog = VaultDialog.NONE },
        )
        VaultDialog.RENAME_FOLDER -> FolderNameDialog(
            title = stringResource(R.string.vault_folder_rename),
            initial = folder?.title.orEmpty(),
            onConfirm = {
                onRenameFolder(it)
                dialog = VaultDialog.NONE
            },
            onDismiss = { dialog = VaultDialog.NONE },
        )
        // Única confirmación de la app: borrar una carpeta arrastra todo lo que contiene y no se puede deshacer.
        VaultDialog.DELETE_FOLDER -> AlertDialog(
            onDismissRequest = { dialog = VaultDialog.NONE },
            title = { Text(stringResource(R.string.vault_folder_delete_title, folder?.title.orEmpty())) },
            text = { Text(stringResource(R.string.vault_folder_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteFolder()
                        dialog = VaultDialog.NONE
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { dialog = VaultDialog.NONE }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.vault_search_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.vault_search_clear))
                }
            }
        },
    )
}

private fun androidx.compose.foundation.lazy.LazyListScope.searchResults(
    uiState: VaultUiState,
    onOpenFolder: (Long) -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    if (uiState.results.isEmpty()) {
        item(key = "search-empty") {
            EmptyState(
                title = stringResource(R.string.vault_search_empty_title),
                message = stringResource(R.string.vault_search_empty_message),
            )
        }
        return
    }
    items(uiState.results, key = { "result-${it.node.id}" }) { result ->
        val node = result.node
        FocusCard(onClick = { if (node.isFolder) onOpenFolder(node.id) else onOpenNote(node.id) }) {
            Text(
                text = node.title.ifBlank { stringResource(R.string.vault_untitled) },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (node.isFolder) stringResource(R.string.vault_folder_kind) else result.path.ifEmpty { stringResource(R.string.nav_vault) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (result.snippet.isNotEmpty()) {
                Text(
                    text = result.snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.contents(
    uiState: VaultUiState,
    onNewFolder: () -> Unit,
    onOpenFolder: (Long) -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    item(key = "contents-header") {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(
                text = uiState.currentFolder?.title ?: stringResource(R.string.vault_notes_header),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onNewFolder, modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
                Text(stringResource(R.string.vault_new_folder), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    if (uiState.folders.isEmpty() && uiState.notes.isEmpty() && !uiState.loading) {
        item(key = "contents-empty") {
            Text(
                text = stringResource(if (uiState.folderId == null) R.string.vault_notes_empty else R.string.vault_folder_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.sm),
            )
        }
    }
    items(uiState.folders, key = { "folder-${it.id}" }) { folder ->
        LinkCard(
            title = folder.title,
            description = stringResource(R.string.vault_folder_kind),
            onClick = { onOpenFolder(folder.id) },
        )
    }
    items(uiState.notes, key = { "note-${it.id}" }) { note ->
        NoteCard(note = note, onClick = { onOpenNote(note.id) })
    }
}

@Composable
private fun NoteCard(note: VaultNode, onClick: () -> Unit) {
    FocusCard(onClick = onClick) {
        Text(
            text = note.title.ifBlank { stringResource(R.string.vault_untitled) },
            style = MaterialTheme.typography.titleMedium,
        )
        if (note.tags.isNotEmpty()) {
            Text(
                text = note.tags.joinToString(" ") { "#$it" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.inbox(
    uiState: VaultUiState,
    onCapture: () -> Unit,
    onMakeTask: (BrainDump) -> Unit,
    onSaveToVault: (BrainDump) -> Unit,
    onDiscard: (BrainDump) -> Unit,
    onAddTile: (() -> Unit)?,
    onDismissTileOffer: () -> Unit,
) {
    item(key = "capture") {
        PrimaryActionButton(text = stringResource(R.string.capture_action), onClick = onCapture)
    }

    if (uiState.tileOffer && onAddTile != null) {
        item(key = "tile-offer") {
            TileOfferCard(onAdd = onAddTile, onDismiss = onDismissTileOffer)
        }
    }

    item(key = "inbox-header") {
        SectionHeader(
            text = if (uiState.ideas.isEmpty()) {
                stringResource(R.string.inbox_header)
            } else {
                pluralStringResource(R.plurals.inbox_waiting, uiState.ideas.size, uiState.ideas.size)
            },
        )
    }
    if (uiState.ideas.isEmpty() && !uiState.loading) {
        item(key = "inbox-empty") {
            EmptyState(
                title = stringResource(R.string.inbox_empty_title),
                message = stringResource(R.string.inbox_empty_message),
            )
        }
    }
    items(uiState.ideas, key = { "idea-${it.id}" }) { idea ->
        IdeaCard(
            idea = idea,
            onMakeTask = { onMakeTask(idea) },
            onSaveToVault = { onSaveToVault(idea) },
            onDiscard = { onDiscard(idea) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderNameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            FolderNameForm(
                title = title,
                name = name,
                onNameChange = { name = it },
                onConfirm = { onConfirm(name) },
                onDismiss = onDismiss,
            )
        }
    }
}

/** Nombre de una carpeta nueva o renombrada. Con el campo ya enfocado y el teclado abierto; no deja confirmar en blanco. */
@Composable
internal fun FolderNameForm(
    title: String,
    name: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(
        modifier = Modifier.padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus),
            label = { Text(stringResource(R.string.vault_folder_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onConfirm() }),
        )
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End)) {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
                Text(stringResource(R.string.action_cancel))
            }
            TextButton(enabled = name.isNotBlank(), onClick = onConfirm, modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun IdeaCard(
    idea: BrainDump,
    onMakeTask: () -> Unit,
    onSaveToVault: () -> Unit,
    onDiscard: () -> Unit,
) {
    val title = IdeaText.titleOf(idea.content)
    val whenText = remember(idea.timestamp) { idea.timestamp.formatWhen() }
    FocusCard {
        Text(
            text = idea.content,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = whenText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // FlowRow: con la letra del sistema muy grande las acciones pasan a otra línea en vez de cortarse.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            IdeaAction(R.string.idea_make_task, R.string.idea_make_task_for, title, onMakeTask)
            IdeaAction(R.string.idea_save_to_vault, R.string.idea_save_to_vault_for, title, onSaveToVault)
            IdeaAction(R.string.idea_discard, R.string.idea_discard_for, title, onDiscard)
        }
    }
}

// El texto visible es corto; el lector de pantalla además dice sobre qué idea actúa, porque varias tarjetas repiten botones.
@Composable
private fun IdeaAction(labelRes: Int, descriptionRes: Int, title: String, onClick: () -> Unit) {
    val description = stringResource(descriptionRes, title)
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = Sizes.touchTarget)
            .semantics { contentDescription = description },
    ) {
        Text(text = stringResource(labelRes), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun TileOfferCard(onAdd: () -> Unit, onDismiss: () -> Unit) {
    FocusCard {
        Text(text = stringResource(R.string.tile_offer_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.tile_offer_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            TextButton(onClick = onAdd, modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
                Text(text = stringResource(R.string.tile_offer_add), style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
                Text(text = stringResource(R.string.tile_offer_later), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** «vie 9 oct, 14:30», en la zona y el formato del móvil. */
private fun Instant.formatWhen(): String {
    val local = atZone(ZoneId.systemDefault())
    return "${local.toLocalDate().formatShort()}, ${local.toLocalTime().formatShort()}"
}
