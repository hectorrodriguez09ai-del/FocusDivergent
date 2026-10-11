package com.focusdivergent.app.presentation.vault

import androidx.lifecycle.SavedStateHandle
import com.focusdivergent.app.domain.model.VaultNode
import com.focusdivergent.app.domain.usecase.DeleteVaultNodeUseCase
import com.focusdivergent.app.domain.usecase.FakeVault
import com.focusdivergent.app.domain.usecase.SaveNoteUseCase
import com.focusdivergent.app.domain.usecase.TEST_CLOCK
import com.focusdivergent.app.domain.vault.VaultTrash
import com.focusdivergent.app.presentation.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NoteEditorViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val vault = FakeVault()
    private val trash = VaultTrash()

    private fun viewModel(noteId: Long = 0, parentId: Long = 0) = NoteEditorViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(NoteEditorViewModel.ARG_NOTE_ID to noteId, NoteEditorViewModel.ARG_PARENT_ID to parentId),
        ),
        vault = vault,
        saveNote = SaveNoteUseCase(vault, TEST_CLOCK),
        deleteNode = DeleteVaultNodeUseCase(vault, trash),
    )

    private suspend fun NoteEditorViewModel.closed(): Boolean =
        withTimeoutOrNull(100) { events.first() } == NoteEditorEvent.Close

    private val autosave = NoteEditorViewModel.AUTOSAVE_MS

    @Test
    fun `una nota nueva arranca vacia y lista para escribir`() {
        val state = viewModel().uiState.value

        assertFalse(state.loading)
        assertEquals("", state.title)
        assertEquals("", state.body)
        assertFalse(state.preview)
    }

    @Test
    fun `no se guarda hasta que el texto se queda quieto, y entonces se guarda sola`() = runTest {
        val vm = viewModel()

        vm.setTitle("Escalas")
        vm.setBody("Pentatónica menor")
        advanceTimeBy(autosave - 100)
        assertTrue("todavía no", vault.items.isEmpty())
        assertFalse(vm.uiState.value.saved)

        advanceTimeBy(200)
        runCurrent()

        val note = vault.items.single()
        assertEquals("Escalas", note.title)
        assertEquals("Pentatónica menor", note.markdownContent)
        assertTrue(vm.uiState.value.saved)
    }

    @Test
    fun `seguir escribiendo actualiza la misma nota, no crea otra`() = runTest {
        val vm = viewModel()
        vm.setBody("uno")
        advanceTimeBy(autosave + 1)
        runCurrent()

        vm.setBody("uno dos")
        advanceTimeBy(autosave + 1)
        runCurrent()

        assertEquals(listOf("uno dos"), vault.items.map { it.markdownContent })
    }

    @Test
    fun `una nota nueva sin nada escrito no se crea, ni con autoguardado ni al salir`() = runTest {
        val vm = viewModel()

        vm.setTitle("   ")
        advanceTimeBy(autosave * 2)
        vm.saveAndClose()

        assertTrue(vm.closed())
        assertTrue(vault.items.isEmpty())
    }

    @Test
    fun `salir guarda lo escrito al instante y cierra solo cuando ya esta guardado`() = runTest {
        val vm = viewModel()
        vm.setBody("Idea que no puede perderse")

        vm.saveAndClose()

        assertEquals("Idea que no puede perderse", vault.items.single().markdownContent)
        assertTrue(vm.closed())
    }

    @Test
    fun `al irse a otra app se guarda sin esperar`() = runTest {
        val vm = viewModel()
        vm.setTitle("A medias")

        vm.flush()

        assertEquals("A medias", vault.items.single().title)
    }

    @Test
    fun `sin titulo la lista muestra la primera linea del cuerpo`() = runTest {
        val vm = viewModel()
        vm.setBody("Llamar a Marta\npor el viaje")

        vm.flush()

        assertEquals("Llamar a Marta", vault.items.single().title)
    }

    @Test
    fun `una nota nueva se crea en la carpeta desde la que se pidio`() = runTest {
        val folderId = vault.save(VaultNode(title = "Música"))
        val vm = viewModel(parentId = folderId)

        vm.setTitle("Escalas")
        vm.flush()

        assertEquals(folderId, vault.items.single { !it.isFolder }.parentId)
        assertEquals("Música", vm.uiState.value.folderLabel)
    }

    // ---- Abrir una existente

    private suspend fun existingNote(updatedAt: Instant = Instant.EPOCH) = vault.save(
        VaultNode(title = "Receta", markdownContent = "# Pan", tags = listOf("cocina"), updatedAt = updatedAt),
    )

    @Test
    fun `abrir una nota existente carga su contenido`() = runTest {
        val vm = viewModel(noteId = existingNote())

        val state = vm.uiState.first { !it.loading }

        assertEquals("Receta", state.title)
        assertEquals("# Pan", state.body)
        assertEquals(listOf("cocina"), state.tags)
        assertTrue(state.saved)
    }

    @Test
    fun `abrir una nota y no tocar nada no la vuelve a guardar`() = runTest {
        val vm = viewModel(noteId = existingNote(updatedAt = Instant.EPOCH))
        vm.uiState.first { !it.loading }

        advanceTimeBy(autosave * 2)
        vm.flush()

        assertEquals(Instant.EPOCH, vault.items.single().updatedAt)
    }

    @Test
    fun `editar una nota existente la actualiza con la hora de ahora`() = runTest {
        val id = existingNote()
        val vm = viewModel(noteId = id)
        vm.uiState.first { !it.loading }

        vm.setBody("# Pan de centeno")
        vm.flush()

        val note = vault.getById(id)!!
        assertEquals("# Pan de centeno", note.markdownContent)
        assertEquals(TEST_CLOCK.instant(), note.updatedAt)
    }

    @Test
    fun `abrir una nota que no existe, o una carpeta, cierra el editor`() = runTest {
        assertTrue(viewModel(noteId = 99).closed())

        val folder = vault.save(VaultNode(title = "Carpeta"))
        assertTrue(viewModel(noteId = folder).closed())
    }

    // ---- Etiquetas

    @Test
    fun `las etiquetas se anaden limpias, sin repetir, y se quitan`() = runTest {
        val vm = viewModel()

        vm.setTagInput(" #Música ")
        vm.addTag()
        vm.setTagInput("música")
        vm.addTag()
        vm.setTagInput("dos palabras")
        vm.addTag()

        assertEquals(listOf("Música", "dos_palabras"), vm.uiState.value.tags)
        assertEquals("", vm.uiState.value.tagInput)

        vm.removeTag("Música")
        assertEquals(listOf("dos_palabras"), vm.uiState.value.tags)
    }

    @Test
    fun `lo que queda escrito en el campo de etiquetas se anade al salir`() = runTest {
        val vm = viewModel()
        vm.setBody("Algo")
        vm.setTagInput("pendiente")

        vm.saveAndClose()

        assertEquals(listOf("pendiente"), vault.items.single().tags)
    }

    @Test
    fun `se sugieren las etiquetas de otras notas que esta aun no tiene`() = runTest {
        vault.save(VaultNode(title = "A", markdownContent = "", tags = listOf("Ideas", "Música")))
        vault.save(VaultNode(title = "B", markdownContent = "", tags = listOf("Música")))
        val vm = viewModel()

        assertEquals(listOf("Música", "Ideas"), vm.uiState.value.suggestions)

        vm.addSuggestedTag("Música")
        assertEquals(listOf("Ideas"), vm.uiState.value.suggestions)
        assertEquals(listOf("Música"), vm.uiState.value.tags)
    }

    // ---- Mover y borrar

    @Test
    fun `mover la nota a otra carpeta se guarda al momento`() = runTest {
        val music = vault.save(VaultNode(title = "Música"))
        val guitar = vault.save(VaultNode(parentId = music, title = "Guitarra"))
        val id = existingNote()
        val vm = viewModel(noteId = id)
        vm.uiState.first { !it.loading }

        vm.moveTo(guitar)

        assertEquals(guitar, vault.getById(id)?.parentId)
        assertEquals("Música › Guitarra", vm.uiState.value.folderLabel)
        // La raíz primero y después las carpetas por orden alfabético.
        assertEquals(listOf(null, guitar, music), vm.uiState.value.folders.map { it.id })

        vm.moveTo(null)
        assertNull(vault.getById(id)?.parentId)
    }

    @Test
    fun `borrar deja la nota en la papelera, cierra, y un autoguardado pendiente no la resucita`() = runTest {
        val id = existingNote()
        val vm = viewModel(noteId = id)
        vm.uiState.first { !it.loading }
        vm.setBody("cambio sin guardar todavía")

        vm.delete()
        advanceTimeBy(autosave * 2)
        runCurrent()

        assertTrue(vm.closed())
        assertTrue(vault.items.isEmpty())
        assertEquals(id, trash.lastDeleted.value?.id)
    }

    @Test
    fun `borrar una nota que nunca llego a guardarse solo cierra`() = runTest {
        val vm = viewModel()
        vm.setBody("a")
        // Nada guardado aún: se descarta sin dejar nada en la papelera.
        vm.delete()

        assertTrue(vm.closed())
        assertTrue(vault.items.isEmpty())
        assertNull(trash.lastDeleted.value)
    }

    // ---- Vista previa

    @Test
    fun `la vista previa se activa y se desactiva sin tocar el texto`() {
        val vm = viewModel()
        vm.setBody("# Hola")

        vm.setPreview(true)
        assertTrue(vm.uiState.value.preview)
        assertEquals("# Hola", vm.uiState.value.body)

        vm.setPreview(false)
        assertFalse(vm.uiState.value.preview)
    }
}
