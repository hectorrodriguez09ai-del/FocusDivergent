package com.focusdivergent.app.presentation.vault

import androidx.lifecycle.SavedStateHandle
import com.focusdivergent.app.domain.model.VaultNode
import com.focusdivergent.app.domain.usecase.DeleteVaultNodeUseCase
import com.focusdivergent.app.domain.usecase.FakeBrainDump
import com.focusdivergent.app.domain.usecase.FakeCaptureTileOffer
import com.focusdivergent.app.domain.usecase.FakeVault
import com.focusdivergent.app.domain.usecase.RestoreDeletedNoteUseCase
import com.focusdivergent.app.domain.usecase.SaveFolderUseCase
import com.focusdivergent.app.domain.usecase.SaveIdeaToVaultUseCase
import com.focusdivergent.app.domain.usecase.TEST_CLOCK
import com.focusdivergent.app.domain.usecase.TEST_NOW
import com.focusdivergent.app.domain.vault.VaultTrash
import com.focusdivergent.app.presentation.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Carpetas, notas, búsqueda y papelera de la pestaña Bóveda. */
class VaultBrowsingViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val vault = FakeVault()
    private val trash = VaultTrash()
    private val brainDump = FakeBrainDump()

    private fun viewModel(startFolder: Long = 0) = VaultViewModel(
        savedState = SavedStateHandle(mapOf("folder" to startFolder)),
        ideas = brainDump,
        vault = vault,
        offer = FakeCaptureTileOffer(),
        trash = trash,
        saveIdeaToVault = SaveIdeaToVaultUseCase(brainDump, vault, TEST_CLOCK),
        saveFolder = SaveFolderUseCase(vault, TEST_CLOCK),
        deleteNode = DeleteVaultNodeUseCase(vault, trash),
        restoreNote = RestoreDeletedNoteUseCase(vault, trash),
    )

    private suspend fun VaultViewModel.state(until: (VaultUiState) -> Boolean = { true }) =
        uiState.first { !it.loading && until(it) }

    private suspend fun folder(title: String, parent: Long? = null) = vault.save(VaultNode(parentId = parent, title = title))

    private suspend fun note(title: String, parent: Long? = null, body: String = "texto", minutesAgo: Long = 0, tags: List<String> = emptyList()) =
        vault.save(
            VaultNode(
                parentId = parent,
                title = title,
                markdownContent = body,
                tags = tags,
                updatedAt = TEST_NOW.minusSeconds(minutesAgo * 60),
            ),
        )

    @Test
    fun `la raiz separa carpetas y notas, y las notas van de la mas reciente a la mas antigua`() = runTest {
        folder("Zeta")
        folder("Alfa")
        note("Vieja", minutesAgo = 60)
        note("Nueva", minutesAgo = 1)
        note("Media", minutesAgo = 20)

        val state = viewModel().state()

        assertEquals(listOf("Alfa", "Zeta"), state.folders.map { it.title })
        assertEquals(listOf("Nueva", "Media", "Vieja"), state.notes.map { it.title })
        assertNull(state.folderId)
        assertTrue(state.path.isEmpty())
    }

    @Test
    fun `entrar en una carpeta enseña su contenido y su ruta, y atras sube un nivel`() = runTest {
        val music = folder("Música")
        val guitar = folder("Guitarra", music)
        note("En guitarra", guitar)
        note("En música", music)
        note("En la raíz")
        val vm = viewModel()

        vm.openFolder(guitar)
        val inGuitar = vm.state { it.folderId == guitar }
        assertEquals(listOf("Música", "Guitarra"), inGuitar.path.map { it.title })
        assertEquals(listOf("En guitarra"), inGuitar.notes.map { it.title })

        vm.up()
        val inMusic = vm.state { it.folderId == music }
        assertEquals(listOf("Guitarra"), inMusic.folders.map { it.title })
        assertEquals(listOf("En música"), inMusic.notes.map { it.title })

        vm.up()
        assertEquals(listOf("En la raíz"), vm.state { it.folderId == null }.notes.map { it.title })

        vm.up()
        assertNull(vm.state().folderId)
    }

    @Test
    fun `si la carpeta que se estaba viendo ya no existe, se muestra la raiz`() = runTest {
        note("En la raíz")

        val state = viewModel(startFolder = 99).state()

        assertNull(state.folderId)
        assertEquals(listOf("En la raíz"), state.notes.map { it.title })
    }

    // ---- Búsqueda

    @Test
    fun `buscar enseña notas y carpetas con su ruta y el trozo donde aparece`() = runTest {
        val music = folder("Música")
        note("Escalas", music, body = "Practicar la pentatónica menor todos los días")
        folder("Pentatónica antigua")
        note("Receta", body = "Pan de centeno")
        val vm = viewModel()

        vm.setQuery("pentat")
        val state = vm.state { it.results.size == 2 }

        assertTrue(state.isSearching)
        val noteResult = state.results.single { !it.node.isFolder }
        assertEquals("Música", noteResult.path)
        assertTrue(noteResult.snippet.contains("pentatónica"))
        assertTrue(state.results.any { it.node.isFolder })
    }

    @Test
    fun `una busqueda sin palabras no enseña resultados y atras sale de la busqueda antes de subir`() = runTest {
        val music = folder("Música")
        val vm = viewModel(startFolder = music)
        vm.setQuery("   ")
        assertTrue(vm.state().results.isEmpty())

        vm.setQuery("algo")
        vm.state { it.isSearching }
        vm.up()

        val state = vm.state { !it.isSearching }
        assertEquals(music, state.folderId)
    }

    @Test
    fun `abrir una carpeta desde los resultados limpia la busqueda`() = runTest {
        val music = folder("Música")
        val vm = viewModel()
        vm.setQuery("mús")
        vm.state { it.results.isNotEmpty() }

        vm.openFolder(music)

        val state = vm.state { it.folderId == music }
        assertEquals("", state.query)
    }

    // ---- Carpetas

    @Test
    fun `crear una carpeta la deja dentro de la que se esta viendo`() = runTest {
        val music = folder("Música")
        val vm = viewModel(startFolder = music)

        vm.createFolder("  Guitarra ")

        val state = vm.state { it.folders.isNotEmpty() }
        assertEquals(listOf("Guitarra"), state.folders.map { it.title })
        assertEquals(music, vault.items.single { it.title == "Guitarra" }.parentId)
    }

    @Test
    fun `crear una carpeta sin nombre no hace nada`() = runTest {
        val vm = viewModel()
        vm.createFolder("   ")
        assertTrue(vault.items.isEmpty())
    }

    @Test
    fun `renombrar la carpeta actual conserva su contenido`() = runTest {
        val music = folder("Música")
        note("Escalas", music)
        val vm = viewModel(startFolder = music)
        vm.state()

        vm.renameCurrentFolder("Instrumentos")

        val state = vm.state { it.currentFolder?.title == "Instrumentos" }
        assertEquals(listOf("Escalas"), state.notes.map { it.title })
    }

    @Test
    fun `borrar la carpeta actual se lleva su contenido y sube a la carpeta madre`() = runTest {
        val music = folder("Música")
        val guitar = folder("Guitarra", music)
        note("Escalas", guitar)
        note("Fuera")
        val vm = viewModel(startFolder = guitar)
        vm.state()

        vm.deleteCurrentFolder()

        val state = vm.state { it.folderId == music }
        assertEquals(listOf("Música"), state.path.map { it.title })
        assertEquals(listOf("Fuera"), vault.items.filter { !it.isFolder }.map { it.title })
        assertTrue(state.deletedNote == null)
    }

    // ---- Papelera

    @Test
    fun `una nota borrada en el editor se puede deshacer desde la lista`() = runTest {
        val id = note("Efímera")
        val deleted = vault.getById(id)!!
        val vm = viewModel()
        vm.state()

        DeleteVaultNodeUseCase(vault, trash)(deleted)
        assertEquals(deleted, vm.state { it.deletedNote != null }.deletedNote)

        vm.undoDeleteNote()

        assertEquals(listOf("Efímera"), vm.state { it.notes.isNotEmpty() }.notes.map { it.title })
        assertNull(vm.state().deletedNote)
    }

    @Test
    fun `dejar pasar el aviso hace definitivo el borrado`() = runTest {
        val id = note("Efímera")
        val deleted = vault.getById(id)!!
        val vm = viewModel()
        DeleteVaultNodeUseCase(vault, trash)(deleted)
        vm.state { it.deletedNote != null }

        vm.dismissDeletedNote(deleted)

        assertNull(vm.state { it.deletedNote == null }.deletedNote)
        assertTrue(vault.items.isEmpty())
    }
}
