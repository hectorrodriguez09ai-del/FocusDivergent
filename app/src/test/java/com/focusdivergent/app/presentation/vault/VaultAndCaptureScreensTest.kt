package com.focusdivergent.app.presentation.vault

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.focusdivergent.app.domain.model.BrainDump
import com.focusdivergent.app.domain.model.VaultNode
import com.focusdivergent.app.domain.usecase.TEST_NOW
import com.focusdivergent.app.presentation.capture.CaptureContent
import com.focusdivergent.app.presentation.theme.FocusDivergentTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La bandeja del Brain Dump y la captura rápida, dibujadas con estado fijo (sin ViewModel ni base de datos). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "es-rES-w411dp-h2000dp")
class VaultAndCaptureScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val marta = BrainDump(id = 1, content = "Llamar a Marta por el viaje", timestamp = TEST_NOW)
    private val huerto = BrainDump(id = 2, content = "Quizá un huerto\nen la terraza", timestamp = TEST_NOW)

    private fun vault(
        state: VaultUiState,
        onCapture: () -> Unit = {},
        onMakeTask: (BrainDump) -> Unit = {},
        onSaveToVault: (BrainDump) -> Unit = {},
        onDiscard: (BrainDump) -> Unit = {},
        onAddTile: (() -> Unit)? = null,
        onDismissTileOffer: () -> Unit = {},
        onQuery: (String) -> Unit = {},
        onUp: () -> Unit = {},
        onOpenFolder: (Long) -> Unit = {},
        onOpenNote: (Long) -> Unit = {},
        onNewNote: () -> Unit = {},
        onCreateFolder: (String) -> Unit = {},
        onRenameFolder: (String) -> Unit = {},
        onDeleteFolder: () -> Unit = {},
    ) = compose.setContent {
        FocusDivergentTheme {
            VaultContent(
                uiState = state,
                onCapture = onCapture,
                onMakeTask = onMakeTask,
                onSaveToVault = onSaveToVault,
                onDiscard = onDiscard,
                onAddTile = onAddTile,
                onDismissTileOffer = onDismissTileOffer,
                onQuery = onQuery,
                onUp = onUp,
                onOpenFolder = onOpenFolder,
                onOpenNote = onOpenNote,
                onNewNote = onNewNote,
                onCreateFolder = onCreateFolder,
                onRenameFolder = onRenameFolder,
                onDeleteFolder = onDeleteFolder,
            )
        }
    }

    // ---- Bandeja

    @Test
    fun `sin ideas la bandeja lo dice con calma y el boton de anotar sigue ahi`() {
        var captured = 0
        vault(VaultUiState(loading = false), onCapture = { captured++ })

        compose.onNodeWithText("Nada esperando").assertExists()
        compose.onNodeWithText("Anotar idea").performClick()

        assertEquals(1, captured)
    }

    @Test
    fun `con ideas la cabecera las cuenta en singular y en plural`() {
        vault(VaultUiState(ideas = listOf(marta), loading = false))
        compose.onNodeWithText("1 idea esperando").assertExists()
    }

    @Test
    fun `con varias ideas se cuentan y se ve el texto de cada una`() {
        vault(VaultUiState(ideas = listOf(marta, huerto), loading = false))

        compose.onNodeWithText("2 ideas esperando").assertExists()
        compose.onNodeWithText("Llamar a Marta por el viaje").assertExists()
        compose.onNodeWithText("Quizá un huerto\nen la terraza").assertExists()
    }

    @Test
    fun `cada accion actua sobre su idea y el lector de pantalla sabe sobre cual`() {
        val made = mutableListOf<Long>()
        val saved = mutableListOf<Long>()
        val dropped = mutableListOf<Long>()
        vault(
            VaultUiState(ideas = listOf(marta, huerto), loading = false),
            onMakeTask = { made += it.id },
            onSaveToVault = { saved += it.id },
            onDiscard = { dropped += it.id },
        )

        compose.onNodeWithContentDescription("Hacer tarea: Llamar a Marta por el viaje").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Guardar en la Bóveda: Quizá un huerto").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Descartar: Quizá un huerto").performScrollTo().performClick()

        assertEquals(listOf(1L), made)
        assertEquals(listOf(2L), saved)
        assertEquals(listOf(2L), dropped)
    }

    @Test
    fun `las notas de la raiz se listan por titulo`() {
        vault(VaultUiState(notes = listOf(VaultNode(id = 3, title = "Música", markdownContent = "x")), loading = false))

        compose.onNodeWithText("En la Bóveda").assertExists()
        compose.onNodeWithText("Música").assertExists()
    }

    // ---- Oferta del mosaico

    @Test
    fun `la oferta del mosaico se ofrece, se acepta o se pospone`() {
        var added = 0
        var dismissed = 0
        vault(
            VaultUiState(tileOffer = true, loading = false),
            onAddTile = { added++ },
            onDismissTileOffer = { dismissed++ },
        )

        compose.onNodeWithText("Anota ideas desde cualquier sitio").assertExists()
        compose.onNodeWithText("Añadir a Ajustes rápidos").performClick()
        compose.onNodeWithText("Ahora no").performClick()

        assertEquals(1, added)
        assertEquals(1, dismissed)
    }

    @Test
    fun `sin soporte para anadir el mosaico, o ya respondida, no se ofrece`() {
        vault(VaultUiState(tileOffer = true, loading = false), onAddTile = null)
        compose.onNodeWithText("Anota ideas desde cualquier sitio").assertDoesNotExist()
    }

    @Test
    fun `una oferta ya respondida no vuelve a aparecer`() {
        vault(VaultUiState(tileOffer = false, loading = false), onAddTile = {})
        compose.onNodeWithText("Anota ideas desde cualquier sitio").assertDoesNotExist()
    }

    // ---- Captura

    @Test
    fun `la captura enseña el campo, avisa de que se guarda sola y guarda con el boton`() {
        var submitted = 0
        compose.setContent {
            FocusDivergentTheme { CaptureContent(text = "", onTextChange = {}, onSubmit = { submitted++ }) }
        }

        compose.onNodeWithText("¿Qué se te ha ocurrido?").assertExists()
        compose.onNodeWithText("Se guarda sola si sales. Luego decides qué hacer con ella.").assertExists()
        compose.onNodeWithText("Guardar").performClick()

        assertEquals(1, submitted)
    }

    @Test
    fun `lo que se escribe llega al estado`() {
        val typed = StringBuilder()
        compose.setContent {
            FocusDivergentTheme {
                CaptureContent(text = "", onTextChange = { typed.append(it) }, onSubmit = {})
            }
        }

        compose.onNodeWithText("¿Qué se te ha ocurrido?").performTextInput("Una idea")

        assertTrue(typed.toString().contains("Una idea"))
    }
}
