package com.focusdivergent.app.presentation.vault

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusdivergent.app.domain.braindump.IdeaText
import com.focusdivergent.app.domain.model.BrainDump
import com.focusdivergent.app.domain.model.VaultNode
import com.focusdivergent.app.domain.repository.BrainDumpRepository
import com.focusdivergent.app.domain.repository.VaultRepository
import com.focusdivergent.app.domain.settings.CaptureTileOffer
import com.focusdivergent.app.domain.usecase.DeleteVaultNodeUseCase
import com.focusdivergent.app.domain.usecase.RestoreDeletedNoteUseCase
import com.focusdivergent.app.domain.usecase.SaveFolderUseCase
import com.focusdivergent.app.domain.usecase.SaveIdeaToVaultUseCase
import com.focusdivergent.app.domain.vault.VaultPaths
import com.focusdivergent.app.domain.vault.VaultSnippet
import com.focusdivergent.app.domain.vault.VaultTrash
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Aviso breve tras una acción sobre una idea de la bandeja. Solo hay uno a la vez: el último. */
sealed interface VaultNotice {
    /** Idea descartada; mientras el aviso está a la vista se puede deshacer. */
    data class Discarded(val idea: BrainDump) : VaultNotice

    /** Idea guardada en la Bóveda como la nota [title]. */
    data class SavedToVault(val title: String) : VaultNotice
}

/** Un resultado de búsqueda: el nodo, dónde está y el trozo de texto donde aparece lo buscado. */
data class VaultSearchResult(val node: VaultNode, val path: String, val snippet: String)

/**
 * @property folderId carpeta que se está viendo; nulo = raíz.
 * @property path carpetas desde la raíz hasta la actual, ambas incluidas; vacía en la raíz.
 * @property folders carpetas de la carpeta actual, por orden alfabético.
 * @property notes notas de la carpeta actual, la tocada más recientemente primero.
 * @property results resultados de buscar [query]; solo tienen sentido si [isSearching].
 * @property deletedNote nota recién borrada, mientras se puede deshacer.
 */
data class VaultUiState(
    /** Bandeja de entrada: ideas sin procesar, la más reciente primero. */
    val ideas: List<BrainDump> = emptyList(),
    val folderId: Long? = null,
    val path: List<VaultNode> = emptyList(),
    val folders: List<VaultNode> = emptyList(),
    val notes: List<VaultNode> = emptyList(),
    val query: String = "",
    val results: List<VaultSearchResult> = emptyList(),
    val deletedNote: VaultNode? = null,
    val notice: VaultNotice? = null,
    /** Aún no se le ha ofrecido (o no ha respondido) añadir el mosaico de Ajustes rápidos. */
    val tileOffer: Boolean = false,
    val loading: Boolean = true,
) {
    val isSearching: Boolean get() = query.isNotBlank()
    val currentFolder: VaultNode? get() = path.lastOrNull()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VaultViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val ideas: BrainDumpRepository,
    private val vault: VaultRepository,
    private val offer: CaptureTileOffer,
    private val trash: VaultTrash,
    private val saveIdeaToVault: SaveIdeaToVaultUseCase,
    private val saveFolder: SaveFolderUseCase,
    private val deleteNode: DeleteVaultNodeUseCase,
    private val restoreNote: RestoreDeletedNoteUseCase,
) : ViewModel() {

    // 0 = raíz (los ids de la base empiezan en 1). Sobrevive a un giro de pantalla.
    private val folderId = savedState.getStateFlow(KEY_FOLDER, 0L)
    private val query = MutableStateFlow("")
    private val notice = MutableStateFlow<VaultNotice?>(null)

    private data class Location(val path: List<VaultNode>, val byId: Map<Long, VaultNode>) {
        val id: Long? get() = path.lastOrNull()?.id
    }

    // Si la carpeta que se estaba viendo ya no existe (borrada), la ruta sale vacía y se muestra la raíz.
    private val location: Flow<Location> = combine(folderId, vault.observeFolders()) { id, folders ->
        val byId = folders.associateBy { it.id }
        Location(VaultPaths.folderChain(id.takeIf { it != 0L }, byId), byId)
    }

    private val children: Flow<List<VaultNode>> =
        location.map { it.id }.distinctUntilChanged().flatMapLatest { vault.observeChildren(it) }

    private val found: Flow<List<VaultNode>> =
        query.flatMapLatest { q -> if (q.isBlank()) flowOf(emptyList()) else vault.search(q) }

    private data class Inbox(
        val ideas: List<BrainDump>,
        val tileOffer: Boolean,
        val notice: VaultNotice?,
        val deletedNote: VaultNode?,
    )

    private data class Browse(
        val location: Location,
        val children: List<VaultNode>,
        val query: String,
        val found: List<VaultNode>,
    )

    private val inbox = combine(ideas.observeUnprocessed(), offer.pending, notice, trash.lastDeleted, ::Inbox)
    private val browse = combine(location, children, query, found, ::Browse)

    val uiState: StateFlow<VaultUiState> = combine(inbox, browse) { inbox, browse ->
        VaultUiState(
            ideas = inbox.ideas,
            folderId = browse.location.id,
            path = browse.location.path,
            folders = browse.children.filter { it.isFolder },
            notes = browse.children.filterNot { it.isFolder }.sortedByDescending { it.updatedAt },
            query = browse.query,
            results = browse.found.map { node ->
                VaultSearchResult(
                    node = node,
                    path = VaultPaths.label(node.parentId, browse.location.byId),
                    snippet = VaultSnippet.of(node.markdownContent, browse.query),
                )
            },
            deletedNote = inbox.deletedNote,
            notice = inbox.notice,
            tileOffer = inbox.tileOffer,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaultUiState())

    // ---- Navegación y búsqueda

    fun openFolder(id: Long) {
        query.value = ""
        savedState[KEY_FOLDER] = id
    }

    /** «Atrás» dentro de la Bóveda: primero se sale de la búsqueda; después se sube una carpeta. */
    fun up() {
        if (query.value.isNotBlank()) {
            query.value = ""
            return
        }
        val parent = uiState.value.currentFolder?.parentId
        savedState[KEY_FOLDER] = parent ?: 0L
    }

    fun setQuery(value: String) {
        query.value = value
    }

    // ---- Carpetas (siempre sobre la carpeta que se está viendo)

    fun createFolder(name: String) {
        viewModelScope.launch {
            val parent = folderId.value.takeIf { it != 0L }?.takeIf { vault.getById(it) != null }
            saveFolder(existing = null, parentId = parent, name = name)
        }
    }

    fun renameCurrentFolder(name: String) {
        val folder = uiState.value.currentFolder ?: return
        viewModelScope.launch { saveFolder(existing = folder, parentId = folder.parentId, name = name) }
    }

    /** Borra la carpeta que se está viendo, con todo lo que contiene, y sube a su carpeta madre. */
    fun deleteCurrentFolder() {
        val folder = uiState.value.currentFolder ?: return
        viewModelScope.launch {
            savedState[KEY_FOLDER] = folder.parentId ?: 0L
            deleteNode(folder)
        }
    }

    // ---- Notas borradas

    fun undoDeleteNote() {
        viewModelScope.launch { restoreNote() }
    }

    /** El aviso de «borrada» se cerró sin deshacer: el borrado pasa a ser definitivo. */
    fun dismissDeletedNote(note: VaultNode) = trash.clear(note)

    // ---- Bandeja de entrada

    fun saveToVault(idea: BrainDump) {
        viewModelScope.launch {
            saveIdeaToVault(idea)
            notice.value = VaultNotice.SavedToVault(IdeaText.titleOf(idea.content))
        }
    }

    /** Descartar no pide confirmación; a cambio, durante unos segundos se puede deshacer. */
    fun discard(idea: BrainDump) {
        viewModelScope.launch {
            ideas.delete(idea)
            notice.value = VaultNotice.Discarded(idea)
        }
    }

    /** La idea vuelve a la bandeja con su mismo id y su hora original. */
    fun undoDiscard(idea: BrainDump) {
        viewModelScope.launch {
            ideas.add(idea)
            dismissNotice(VaultNotice.Discarded(idea))
        }
    }

    /** El aviso se cerró: solo se retira si sigue siendo [shown], para que uno antiguo no borre uno más reciente. */
    fun dismissNotice(shown: VaultNotice) {
        notice.compareAndSet(shown, null)
    }

    fun tileOfferHandled() = offer.markHandled()

    private companion object {
        const val KEY_FOLDER = "folder"
    }
}
