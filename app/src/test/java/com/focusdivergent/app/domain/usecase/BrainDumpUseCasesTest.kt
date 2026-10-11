package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.braindump.IdeaText
import com.focusdivergent.app.domain.model.BrainDump
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainDumpUseCasesTest {

    private val brainDump = FakeBrainDump()
    private val vault = FakeVault()

    // ---- Captura

    @Test
    fun `capturar guarda la idea sin espacios sobrantes y con la hora de ahora`() = runTest {
        val saved = CaptureIdeaUseCase(brainDump, TEST_CLOCK)("   Llamar a Marta por el viaje \n")

        assertTrue(saved)
        val idea = brainDump.items.single()
        assertEquals("Llamar a Marta por el viaje", idea.content)
        assertEquals(TEST_NOW, idea.timestamp)
        assertFalse(idea.isProcessed)
    }

    @Test
    fun `capturar un texto vacio o de espacios no guarda nada`() = runTest {
        val capture = CaptureIdeaUseCase(brainDump, TEST_CLOCK)

        assertFalse(capture(""))
        assertFalse(capture("  \n\t "))
        assertTrue(brainDump.items.isEmpty())
    }

    @Test
    fun `una idea capturada aparece en la bandeja y no las ya procesadas`() = runTest {
        val capture = CaptureIdeaUseCase(brainDump, TEST_CLOCK)
        capture("Primera")
        capture("Segunda")
        brainDump.markProcessed(brainDump.items.first().id)

        assertEquals(listOf("Segunda"), brainDump.observeUnprocessed().first().map { it.content })
    }

    // ---- Guardar en la Bóveda

    @Test
    fun `guardar en la Boveda crea una nota en la raiz con el titulo de la primera linea y la idea entera`() = runTest {
        val idea = BrainDump(id = 7, content = "Escala pentatónica\nprobar con el bajo", timestamp = TEST_NOW)
        brainDump.add(idea)

        val noteId = SaveIdeaToVaultUseCase(brainDump, vault, TEST_CLOCK)(idea)

        val note = vault.getById(noteId)!!
        assertNull(note.parentId)
        assertEquals("Escala pentatónica", note.title)
        assertEquals("Escala pentatónica\nprobar con el bajo", note.markdownContent)
        assertFalse(note.isFolder)
        assertEquals(TEST_NOW, note.updatedAt)
    }

    @Test
    fun `guardar en la Boveda saca la idea de la bandeja`() = runTest {
        val idea = BrainDump(id = 3, content = "Algo", timestamp = TEST_NOW)
        brainDump.add(idea)

        SaveIdeaToVaultUseCase(brainDump, vault, TEST_CLOCK)(idea)

        assertTrue(brainDump.observeUnprocessed().first().isEmpty())
    }

    // ---- Título de una idea

    @Test
    fun `el titulo es la primera linea con texto`() {
        assertEquals("Comprar pilas", IdeaText.titleOf("\n  \n  Comprar pilas  \nde botón"))
        assertEquals("", IdeaText.titleOf("   \n "))
    }

    @Test
    fun `un titulo largo se corta en una palabra y lleva puntos suspensivos`() {
        val long = "uno dos tres cuatro cinco seis siete ocho nueve diez once doce trece catorce quince dieciseis"

        val title = IdeaText.titleOf(long)

        assertTrue(title.endsWith("…"))
        assertTrue(title.length <= IdeaText.MAX_TITLE + 1)
        assertTrue("corta en palabra: $title", long.startsWith(title.removeSuffix("…")) && long[title.length - 1] == ' ')
    }

    @Test
    fun `un titulo justo en el limite no se toca`() {
        val exact = "a".repeat(IdeaText.MAX_TITLE)
        assertEquals(exact, IdeaText.titleOf(exact))
    }
}
