package com.focusdivergent.app.presentation.vault

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusdivergent.app.domain.model.VaultNode
import com.focusdivergent.app.domain.repository.VaultRepository
import com.focusdivergent.app.domain.usecase.DeleteVaultNodeUseCase
import com.focusdivergent.app.domain.usecase.SaveNoteUseCase
import com.focusdivergent.app.domain.vault.VaultPaths
import com.focusdivergent.app.domain.vault.VaultTags
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Una carpeta a la que se puede mover la nota. [id] nulo es la raíz de la Bóveda; [label] es su ruta (vacía en la raíz). */
data class FolderOption(val id: Long?, val label: String)

/**
 * @property parentId carpeta donde está la nota; nulo = raíz.
 * @property folderLabel ruta de esa carpeta («Música › Guitarra»); vacía en la raíz.
 * @property saved lo que se ve ya está guardado.
 */
data class NoteEditorUiState(
    val title: String = "",
    val body: String = "",
    val tags: List<String> = emptyList(),
    val tagInput: String = "",
    val suggestions: List<String> = emptyList(),
    val parentId: Long? = null,
    val folderLabel: String = "",
    val folders: List<FolderOption> = emptyList(),
    val preview: Boolean = false,
    val saved: Boolean = false,
    val loading: Boolean = true,
)

sealed interface NoteEditorEvent {
    /** La nota ya está a salvo (o se borró): el editor no tiene nada más que hacer. */
    data object Close : NoteEditorEvent
}

/**
 * Editor de una nota de la Bóveda. **Se guarda solo**: unos instantes después de dejar de escribir, al cambiar de
 * app y al salir. No hay botón «Guardar», porque perder lo escrito es lo peor que le puede pasar a quien usa esto.
 *
 * Una nota nueva solo se crea cuando tiene título o texto, para no llenar la Bóveda de notas vacías.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vault: VaultRepository,
    private val saveNote: SaveNoteUseCase,
    private val deleteNode: DeleteVaultNodeUseCase,
) : ViewModel() {

    // 0 = nota nueva / raíz (los ids de la base empiezan en 1).
    private val noteId: Long = savedStateHandle.get<Long>(ARG_NOTE_ID) ?: 0L
    private val initialParent: Long? = savedStateHandle.get<Long>(ARG_PARENT_ID)?.takeIf { it != 0L }

    /** Lo que se escribe; el resto del estado (carpetas, sugerencias) se le añade desde la base de datos. */
    private data class Editing(
        val title: String = "",
        val body: String = "",
        val tags: List<String> = emptyList(),
        val tagInput: String = "",
        val parentId: Long? = null,
        val preview: Boolean = false,
        val saved: Boolean = false,
        val loading: Boolean = true,
    )

    /** Lo que se guarda: si no cambia, no se vuelve a escribir en la base de datos. */
    private data class Snapshot(val title: String, val body: String, val tags: List<String>, val parentId: Long?)

    private val editing = MutableStateFlow(Editing(parentId = initialParent, loading = noteId != 0L))

    val uiState: StateFlow<NoteEditorUiState> = combine(
        editing,
        vault.observeFolders(),
        vault.observeTags(),
    ) { edit, folders, usedTags ->
        val byId = folders.associateBy { it.id }
        NoteEditorUiState(
            title = edit.title,
            body = edit.body,
            tags = edit.tags,
            tagInput = edit.tagInput,
            suggestions = VaultTags.suggestions(usedTags, edit.tags),
            parentId = edit.parentId,
            folderLabel = VaultPaths.label(edit.parentId, byId),
            folders = listOf(FolderOption(null, "")) + folders.map { FolderOption(it.id, VaultPaths.label(it.id, byId)) },
            preview = edit.preview,
            saved = edit.saved,
            loading = edit.loading,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, NoteEditorUiState())

    private val _events = Channel<NoteEditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val saveLock = Mutex()
    private var existing: VaultNode? = null
    private var lastSaved: Snapshot? = null
    private var deleted = false

    init {
        if (noteId != 0L) {
            viewModelScope.launch {
                val note = vault.getById(noteId)
                if (note == null || note.isFolder) {
                    _events.send(NoteEditorEvent.Close)
                    return@launch
                }
                existing = note
                lastSaved = Snapshot(note.title, note.markdownContent.orEmpty(), note.tags, note.parentId)
                editing.update {
                    it.copy(
                        title = note.title,
                        body = note.markdownContent.orEmpty(),
                        tags = note.tags,
                        parentId = note.parentId,
                        saved = true,
                        loading = false,
                    )
                }
            }
        }

        // Autoguardado: cuando lo escrito se queda quieto AUTOSAVE_MS, se guarda.
        editing
            .map { Snapshot(it.title, it.body, it.tags, it.parentId) }
            .distinctUntilChanged()
            .debounce(AUTOSAVE_MS.milliseconds)
            .onEach { persist() }
            .launchIn(viewModelScope)
    }

    fun setTitle(value: String) = edit { it.copy(title = value) }

    fun setBody(value: String) = edit { it.copy(body = value) }

    fun setPreview(value: Boolean) = editing.update { it.copy(preview = value) }

    fun setTagInput(value: String) = editing.update { it.copy(tagInput = value) }

    /** Añade lo que hay escrito en el campo de etiquetas (si queda algo) y lo vacía. */
    fun addTag() = edit { it.copy(tags = VaultTags.add(it.tags, it.tagInput), tagInput = "") }

    fun addSuggestedTag(tag: String) = edit { it.copy(tags = VaultTags.add(it.tags, tag)) }

    fun removeTag(tag: String) = edit { it.copy(tags = it.tags - tag) }

    /** Mueve la nota a [folderId] (nulo = raíz). Se guarda al momento: es una decisión, no un borrador. */
    fun moveTo(folderId: Long?) {
        edit { it.copy(parentId = folderId) }
        viewModelScope.launch { persist() }
    }

    /** Guarda ya, sin cerrar. Para cuando la pantalla deja de verse (el usuario cambia de app). */
    fun flush() {
        viewModelScope.launch {
            commitPendingTag()
            persist()
        }
    }

    /** Guarda y, solo cuando ya está a salvo, pide cerrar el editor. */
    fun saveAndClose() {
        viewModelScope.launch {
            commitPendingTag()
            persist()
            _events.send(NoteEditorEvent.Close)
        }
    }

    /** Borra la nota (con «Deshacer» en la lista). Una nota que nunca llegó a guardarse simplemente se descarta. */
    fun delete() {
        viewModelScope.launch {
            // Se marca antes de borrar para que un autoguardado pendiente no la resucite.
            deleted = true
            saveLock.withLock { existing?.let { deleteNode(it) } }
            _events.send(NoteEditorEvent.Close)
        }
    }

    private fun commitPendingTag() {
        if (editing.value.tagInput.isNotBlank()) addTag()
    }

    private fun edit(change: (Editing) -> Editing) = editing.update { change(it).copy(saved = false) }

    private suspend fun persist() = saveLock.withLock {
        val current = editing.value
        if (deleted || current.loading) return@withLock
        val snapshot = Snapshot(current.title, current.body, current.tags, current.parentId)
        if (snapshot == lastSaved) {
            editing.update { it.copy(saved = existing != null) }
            return@withLock
        }
        // Una nota nueva todavía vacía no se crea.
        if (existing == null && snapshot.title.isBlank() && snapshot.body.isBlank()) return@withLock

        existing = saveNote(existing, snapshot.parentId, snapshot.title, snapshot.body, snapshot.tags)
        lastSaved = snapshot
        editing.update { it.copy(saved = it.title == snapshot.title && it.body == snapshot.body && it.tags == snapshot.tags) }
    }

    companion object {
        const val ARG_NOTE_ID = "noteId"
        const val ARG_PARENT_ID = "parentId"

        /** Cuánto debe estar quieto el texto para guardarlo solo. */
        const val AUTOSAVE_MS = 1_500L
    }
}
