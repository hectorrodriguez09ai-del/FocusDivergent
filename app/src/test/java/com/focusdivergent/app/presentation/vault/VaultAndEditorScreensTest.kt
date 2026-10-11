package com.focusdivergent.app.presentation.vault

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.focusdivergent.app.domain.model.VaultNode
import com.focusdivergent.app.presentation.theme.FocusDivergentTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La pestaña Bóveda (carpetas, búsqueda, diálogos) y el editor de notas, dibujados con estado fijo. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "es-rES-w411dp-h2000dp")
class VaultAndEditorScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val music = VaultNode(id = 1, title = "Música")
    private val guitar = VaultNode(id = 2, parentId = 1, title = "Guitarra")
    private val scales = VaultNode(id = 3, parentId = 1, title = "Escalas", markdownContent = "x", tags = listOf("teoria", "ideas"))

    private fun vault(
        state: VaultUiState,
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
                onCapture = {},
                onMakeTask = {},
                onSaveToVault = {},
                onDiscard = {},
                onAddTile = null,
                onDismissTileOffer = {},
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

    // ---- Bóveda

    @Test
    fun `la raiz lista carpetas y notas, y se abren con un toque`() {
        val opened = mutableListOf<String>()
        vault(
            VaultUiState(folders = listOf(music), notes = listOf(scales.copy(parentId = null)), loading = false),
            onOpenFolder = { opened += "carpeta $it" },
            onOpenNote = { opened += "nota $it" },
        )

        compose.onNodeWithText("Música").performScrollTo().performClick()
        compose.onNodeWithText("Escalas").performScrollTo().performClick()
        compose.onNodeWithText("#teoria #ideas").assertExists()

        assertEquals(listOf("carpeta 1", "nota 3"), opened)
    }

    @Test
    fun `dentro de una carpeta se ve su nombre, se puede subir y no esta la bandeja ni la captura`() {
        var up = 0
        vault(
            VaultUiState(folderId = 1, path = listOf(music), folders = listOf(guitar), notes = listOf(scales), loading = false),
            onUp = { up++ },
        )

        compose.onNodeWithText("Guitarra").assertExists()
        compose.onNodeWithText("Anotar idea").assertDoesNotExist()
        compose.onNodeWithContentDescription("Atrás").performClick()

        assertEquals(1, up)
    }

    @Test
    fun `una carpeta vacia lo dice con calma`() {
        vault(VaultUiState(folderId = 1, path = listOf(music), loading = false))
        compose.onNodeWithText("Esta carpeta está vacía. Crea una nota o una carpeta cuando quieras.").assertExists()
    }

    @Test
    fun `la raiz vacia invita a crear la primera nota`() {
        vault(VaultUiState(loading = false))
        compose.onNodeWithText("Aún no hay notas. Crea la primera con «Nueva nota», o guarda una idea de la bandeja.").assertExists()
    }

    @Test
    fun `nueva nota abre el editor`() {
        var created = 0
        vault(VaultUiState(loading = false), onNewNote = { created++ })

        // El botón flotante junta su icono y su texto en un solo nodo: se busca el texto en el árbol sin fusionar.
        compose.onNodeWithText("Nueva nota", useUnmergedTree = true).performClick()

        assertEquals(1, created)
    }

    @Test
    fun `escribir en el buscador avisa y con texto aparece el boton de borrar la busqueda`() {
        val typed = mutableListOf<String>()
        vault(VaultUiState(query = "pen", loading = false), onQuery = { typed += it })

        compose.onNodeWithText("Buscar en la Bóveda").performTextInput("ta")
        compose.onNodeWithContentDescription("Borrar la búsqueda").performClick()

        assertTrue(typed.last() == "" && typed.size == 2)
    }

    @Test
    fun `los resultados muestran donde estan y el trozo encontrado, y sin ninguno se dice con calma`() {
        val opened = mutableListOf<Long>()
        vault(
            VaultUiState(
                query = "pentat",
                results = listOf(VaultSearchResult(scales, path = "Música", snippet = "la pentatónica menor")),
                loading = false,
            ),
            onOpenNote = { opened += it },
        )

        compose.onNodeWithText("Música").assertExists()
        compose.onNodeWithText("la pentatónica menor").performClick()
        assertEquals(listOf(3L), opened)
    }

    @Test
    fun `una busqueda sin resultados no es un reproche`() {
        vault(VaultUiState(query = "zzz", loading = false))
        compose.onNodeWithText("Nada encontrado").assertExists()
        compose.onNodeWithText("Nueva nota", useUnmergedTree = true).assertDoesNotExist()
    }

    // ---- Diálogos de carpeta

    @Test
    fun `el formulario de carpeta no deja confirmar en blanco y avisa de lo que se escribe`() {
        val name = mutableStateOf("")
        var confirmed = 0
        compose.setContent {
            FocusDivergentTheme {
                FolderNameForm(
                    title = "Nueva carpeta",
                    name = name.value,
                    onNameChange = { name.value = it },
                    onConfirm = { confirmed++ },
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("Guardar").assertIsNotEnabled()
        compose.onNodeWithText("Nombre de la carpeta").performTextInput("Recetas")
        compose.onNodeWithText("Guardar").assertIsEnabled().performClick()

        assertEquals("Recetas", name.value)
        assertEquals(1, confirmed)
    }

    @Test
    fun `renombrar enseña el nombre actual y cancelar no confirma`() {
        var confirmed = 0
        var dismissed = 0
        compose.setContent {
            FocusDivergentTheme {
                FolderNameForm(
                    title = "Renombrar carpeta",
                    name = "Música",
                    onNameChange = {},
                    onConfirm = { confirmed++ },
                    onDismiss = { dismissed++ },
                )
            }
        }

        compose.onNodeWithText("Música").assertExists()
        compose.onNodeWithText("Guardar").assertIsEnabled()
        compose.onNodeWithText("Cancelar").performClick()

        assertEquals(0, confirmed)
        assertEquals(1, dismissed)
    }

    @Test
    fun `borrar una carpeta pide confirmacion y avisa de que no se puede deshacer`() {
        var deleted = 0
        vault(
            VaultUiState(folderId = 1, path = listOf(music), loading = false),
            onDeleteFolder = { deleted++ },
        )

        compose.onNodeWithContentDescription("Más opciones").performClick()
        compose.onNodeWithText("Borrar carpeta").performClick()
        compose.onNodeWithText("¿Borrar «Música»?").assertExists()
        compose.onNodeWithText("Se borrará la carpeta con todo lo que contiene: notas y carpetas. Esto no se puede deshacer.").assertExists()
        assertEquals("no se borra sin confirmar", 0, deleted)

        compose.onNodeWithText("Borrar").performClick()
        assertEquals(1, deleted)
    }

    @Test
    fun `cancelar la confirmacion no borra nada`() {
        var deleted = 0
        vault(VaultUiState(folderId = 1, path = listOf(music), loading = false), onDeleteFolder = { deleted++ })

        compose.onNodeWithContentDescription("Más opciones").performClick()
        compose.onNodeWithText("Borrar carpeta").performClick()
        compose.onNodeWithText("Cancelar").performClick()

        assertEquals(0, deleted)
        compose.onNodeWithText("¿Borrar «Música»?").assertDoesNotExist()
    }

    // ---- Editor

    private fun editor(
        state: NoteEditorUiState,
        onTitle: (String) -> Unit = {},
        onBody: (String) -> Unit = {},
        onPreview: (Boolean) -> Unit = {},
        onAddTag: () -> Unit = {},
        onSuggestedTag: (String) -> Unit = {},
        onRemoveTag: (String) -> Unit = {},
        onMove: (Long?) -> Unit = {},
        onDelete: () -> Unit = {},
        onBack: () -> Unit = {},
    ) = compose.setContent {
        FocusDivergentTheme {
            NoteEditorContent(
                uiState = state,
                onBack = onBack,
                onTitle = onTitle,
                onBody = onBody,
                onPreview = onPreview,
                onTagInput = {},
                onAddTag = onAddTag,
                onSuggestedTag = onSuggestedTag,
                onRemoveTag = onRemoveTag,
                onMove = onMove,
                onDelete = onDelete,
            )
        }
    }

    private val note = NoteEditorUiState(title = "Receta", body = "# Pan\n\nCon **centeno**", tags = listOf("cocina"), loading = false)

    @Test
    fun `el editor enseña el texto, no tiene boton de guardar y avisa de que se guarda sola`() {
        editor(note)

        compose.onNodeWithText("Receta").assertExists()
        compose.onNodeWithText("Se guarda sola").performScrollTo().assertExists()
        compose.onNodeWithText("Guardar").assertDoesNotExist()
    }

    @Test
    fun `cuando esta guardada lo dice`() {
        editor(note.copy(saved = true))
        compose.onNodeWithText("Guardado").performScrollTo().assertExists()
    }

    @Test
    fun `la vista previa dibuja el Markdown y el boton vuelve a editar`() {
        val toggled = mutableListOf<Boolean>()
        editor(note.copy(preview = true), onPreview = { toggled += it })

        compose.onNodeWithText("Pan").assertExists()
        compose.onNodeWithText("Con centeno").assertExists()
        compose.onNodeWithText("Editar").performClick()

        assertEquals(listOf(false), toggled)
    }

    @Test
    fun `sin texto la vista previa lo dice con calma`() {
        editor(note.copy(body = "", preview = true))
        compose.onNodeWithText("Aún no hay nada que ver.").assertExists()
    }

    @Test
    fun `las etiquetas se pueden quitar y las sugeridas se anaden con un toque`() {
        val removed = mutableListOf<String>()
        val added = mutableListOf<String>()
        editor(note.copy(suggestions = listOf("ideas")), onRemoveTag = { removed += it }, onSuggestedTag = { added += it })

        compose.onNodeWithContentDescription("Quitar la etiqueta cocina").performScrollTo().performClick()
        compose.onNodeWithText("#ideas").performScrollTo().performClick()

        assertEquals(listOf("cocina"), removed)
        assertEquals(listOf("ideas"), added)
    }

    @Test
    fun `mover ofrece la raiz y las carpetas con su ruta`() {
        val moved = mutableListOf<Long?>()
        editor(
            note.copy(
                parentId = 1,
                folderLabel = "Música",
                folders = listOf(FolderOption(null, ""), FolderOption(1, "Música"), FolderOption(2, "Música › Guitarra")),
            ),
            onMove = { moved += it },
        )

        compose.onNodeWithText("En: Música").performScrollTo().assertExists()
        compose.onNodeWithText("Mover").performScrollTo().performClick()
        compose.onNodeWithText("Música › Guitarra").performClick()

        assertEquals(listOf<Long?>(2L), moved)
    }

    @Test
    fun `en la raiz la ubicacion dice Boveda`() {
        editor(note)
        compose.onNodeWithText("En: Bóveda").performScrollTo().assertExists()
    }

    @Test
    fun `borrar y volver avisan al ViewModel`() {
        var deleted = 0
        var back = 0
        editor(note, onDelete = { deleted++ }, onBack = { back++ })

        compose.onNodeWithText("Borrar nota").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Atrás").performClick()

        assertEquals(1, deleted)
        assertEquals(1, back)
    }
}
