package com.focusdivergent.app.presentation.vault

import androidx.lifecycle.SavedStateHandle
import com.focusdivergent.app.domain.model.BrainDump
import com.focusdivergent.app.domain.model.VaultNode
import com.focusdivergent.app.domain.usecase.CaptureIdeaUseCase
import com.focusdivergent.app.domain.usecase.FakeBrainDump
import com.focusdivergent.app.domain.usecase.FakeCaptureTileOffer
import com.focusdivergent.app.domain.usecase.FakeVault
import com.focusdivergent.app.domain.usecase.DeleteVaultNodeUseCase
import com.focusdivergent.app.domain.usecase.RestoreDeletedNoteUseCase
import com.focusdivergent.app.domain.usecase.SaveFolderUseCase
import com.focusdivergent.app.domain.usecase.SaveIdeaToVaultUseCase
import com.focusdivergent.app.domain.vault.VaultTrash
import com.focusdivergent.app.domain.usecase.TEST_CLOCK
import com.focusdivergent.app.domain.usecase.TEST_NOW
import com.focusdivergent.app.presentation.MainDispatcherRule
import com.focusdivergent.app.presentation.capture.CaptureFinished
import com.focusdivergent.app.presentation.capture.CaptureViewModel
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VaultAndCaptureViewModelsTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val brainDump = FakeBrainDump()
    private val vault = FakeVault()
    private val tileOffer = FakeCaptureTileOffer()

    private fun idea(id: Long, content: String, minutesAgo: Long = 0) =
        BrainDump(id = id, content = content, timestamp = TEST_NOW.minus(Duration.ofMinutes(minutesAgo)))

    private val trash = VaultTrash()

    private fun vaultViewModel() = VaultViewModel(
        savedState = SavedStateHandle(),
        ideas = brainDump,
        vault = vault,
        offer = tileOffer,
        trash = trash,
        saveIdeaToVault = SaveIdeaToVaultUseCase(brainDump, vault, TEST_CLOCK),
        saveFolder = SaveFolderUseCase(vault, TEST_CLOCK),
        deleteNode = DeleteVaultNodeUseCase(vault, trash),
        restoreNote = RestoreDeletedNoteUseCase(vault, trash),
    )

    /** El estado solo se calcula con alguien mirando: se pide el primero que ya no está cargando. */
    private suspend fun VaultViewModel.state() = uiState.first { !it.loading }

    // ---- Bandeja

    @Test
    fun `la bandeja enseña las ideas sin procesar, la mas reciente primero, y las notas de la raiz`() = runTest {
        brainDump.add(idea(1, "vieja", minutesAgo = 30))
        brainDump.add(idea(2, "nueva", minutesAgo = 1))
        vault.save(VaultNode(title = "Música", markdownContent = "notas"))
        vault.save(VaultNode(parentId = 1, title = "Dentro de otra carpeta", markdownContent = ""))

        val state = vaultViewModel().state()

        assertEquals(listOf("nueva", "vieja"), state.ideas.map { it.content })
        assertEquals(listOf("Música"), state.notes.map { it.title })
    }

    @Test
    fun `guardar en la Boveda saca la idea de la bandeja, crea la nota y avisa`() = runTest {
        val first = idea(1, "Escala pentatónica\ncon el bajo")
        brainDump.add(first)
        val vm = vaultViewModel()

        vm.saveToVault(first)

        val state = vm.uiState.first { it.notice != null }
        assertTrue(state.ideas.isEmpty())
        assertEquals(listOf("Escala pentatónica"), state.notes.map { it.title })
        assertEquals(VaultNotice.SavedToVault("Escala pentatónica"), state.notice)
    }

    @Test
    fun `descartar borra la idea y deshacer la devuelve con su mismo id y su hora`() = runTest {
        val dropped = idea(5, "Quizá un huerto", minutesAgo = 90)
        brainDump.add(dropped)
        val vm = vaultViewModel()

        vm.discard(dropped)
        assertTrue(vm.uiState.first { it.notice != null }.ideas.isEmpty())
        assertEquals(VaultNotice.Discarded(dropped), vm.uiState.value.notice)

        vm.undoDiscard(dropped)
        val back = vm.uiState.first { it.notice == null && it.ideas.isNotEmpty() }

        assertEquals(listOf(dropped), back.ideas)
    }

    @Test
    fun `cerrar un aviso antiguo no borra uno mas reciente`() = runTest {
        val a = idea(1, "a")
        val b = idea(2, "b")
        brainDump.add(a)
        brainDump.add(b)
        val vm = vaultViewModel()

        vm.discard(a)
        vm.uiState.first { it.notice == VaultNotice.Discarded(a) }
        vm.discard(b)
        vm.uiState.first { it.notice == VaultNotice.Discarded(b) }
        vm.dismissNotice(VaultNotice.Discarded(a))

        assertEquals(VaultNotice.Discarded(b), vm.uiState.value.notice)
    }

    @Test
    fun `la oferta del mosaico se enseña hasta que se responde y entonces no vuelve`() = runTest {
        val vm = vaultViewModel()
        assertTrue(vm.state().tileOffer)

        vm.tileOfferHandled()

        assertFalse(vm.uiState.first { !it.tileOffer }.tileOffer)
    }

    // ---- Captura

    private fun captureViewModel() = CaptureViewModel(SavedStateHandle(), CaptureIdeaUseCase(brainDump, TEST_CLOCK))

    @Test
    fun `enviar guarda la idea y avisa de que se guardo`() = runTest {
        val vm = captureViewModel()

        vm.setText("  Idea suelta ")
        vm.submit()

        assertEquals(CaptureFinished(saved = true), vm.finished.first())
        assertEquals(listOf("Idea suelta"), brainDump.items.map { it.content })
    }

    @Test
    fun `enviar sin texto cierra sin guardar nada`() = runTest {
        val vm = captureViewModel()

        vm.setText("   ")
        vm.submit()

        assertEquals(CaptureFinished(saved = false), vm.finished.first())
        assertTrue(brainDump.items.isEmpty())
    }

    @Test
    fun `salidas repetidas guardan la idea una sola vez`() = runTest {
        val vm = captureViewModel()
        vm.setText("Solo una vez")

        // «Hecho» y, justo después, onStop de la actividad.
        vm.submit()
        vm.submit()
        vm.submit()

        vm.finished.first()
        assertEquals(1, brainDump.items.size)
        assertNull(withTimeoutOrNull(50) { vm.finished.first() })
    }

    @Test
    fun `el texto escrito sobrevive en el estado guardado`() {
        val handle = SavedStateHandle()
        val first = CaptureViewModel(handle, CaptureIdeaUseCase(brainDump, TEST_CLOCK))
        first.setText("a medias")

        val recreated = CaptureViewModel(handle, CaptureIdeaUseCase(brainDump, TEST_CLOCK))

        assertEquals("a medias", recreated.text.value)
    }
}
