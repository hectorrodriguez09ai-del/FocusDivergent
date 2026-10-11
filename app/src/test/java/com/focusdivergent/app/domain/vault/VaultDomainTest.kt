package com.focusdivergent.app.domain.vault

import com.focusdivergent.app.domain.model.VaultNode
import com.focusdivergent.app.domain.usecase.DeleteVaultNodeUseCase
import com.focusdivergent.app.domain.usecase.FakeVault
import com.focusdivergent.app.domain.usecase.RestoreDeletedNoteUseCase
import com.focusdivergent.app.domain.usecase.SaveFolderUseCase
import com.focusdivergent.app.domain.usecase.SaveNoteUseCase
import com.focusdivergent.app.domain.usecase.TEST_CLOCK
import com.focusdivergent.app.domain.usecase.TEST_NOW
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultDomainTest {

    private val vault = FakeVault()
    private val trash = VaultTrash()

    // ---- Etiquetas

    @Test
    fun `una etiqueta se normaliza sin almohadilla ni espacios`() {
        assertEquals("Mi_idea", VaultTags.normalize("  #Mi   idea "))
        assertEquals("", VaultTags.normalize(" # "))
        assertEquals(VaultTags.MAX_LENGTH, VaultTags.normalize("a".repeat(100)).length)
    }

    @Test
    fun `no se anaden etiquetas vacias ni repetidas aunque cambien las mayusculas`() {
        val tags = VaultTags.add(VaultTags.add(emptyList(), "Música"), "música")

        assertEquals(listOf("Música"), tags)
        assertEquals(tags, VaultTags.add(tags, "   "))
    }

    @Test
    fun `las sugerencias omiten las que la nota ya tiene y respetan el limite`() {
        val used = listOf("Ideas", "Música", "Trabajo") + (1..20).map { "t$it" }

        val suggestions = VaultTags.suggestions(used, current = listOf("música"))

        assertEquals(listOf("Ideas", "Trabajo", "t1", "t2", "t3", "t4", "t5", "t6"), suggestions)
    }

    // ---- Rutas

    private fun folders(vararg nodes: VaultNode) = nodes.associateBy { it.id }

    @Test
    fun `la ruta de una carpeta va de la raiz hacia dentro`() {
        val map = folders(
            VaultNode(id = 1, title = "Música"),
            VaultNode(id = 2, parentId = 1, title = "Guitarra"),
        )

        assertEquals(listOf("Música", "Guitarra"), VaultPaths.folderChain(2, map).map { it.title })
        assertEquals("Música › Guitarra", VaultPaths.label(2, map))
        assertEquals("", VaultPaths.label(null, map))
    }

    @Test
    fun `una carpeta que no existe, o un ciclo en los datos, no se cuelga`() {
        assertTrue(VaultPaths.folderChain(9, folders(VaultNode(id = 1, title = "A"))).isEmpty())

        val cycle = folders(VaultNode(id = 1, parentId = 2, title = "A"), VaultNode(id = 2, parentId = 1, title = "B"))
        // No importa qué devuelva mientras termine.
        VaultPaths.folderChain(1, cycle)
    }

    // ---- Fragmentos de búsqueda

    @Test
    fun `el fragmento centra la primera palabra encontrada, sin importar mayusculas ni acentos`() {
        val content = "Intro larga " + "relleno ".repeat(30) + "aquí está la Música que buscabas " + "final ".repeat(30)

        val snippet = VaultSnippet.of(content, "musica")

        assertTrue(snippet, snippet.contains("Música"))
        assertTrue(snippet.startsWith("…") && snippet.endsWith("…"))
        assertTrue(snippet.length <= VaultSnippet.LENGTH + 2)
    }

    @Test
    fun `sin coincidencia en el cuerpo, el fragmento es el principio y un texto corto sale entero`() {
        assertEquals("Una nota corta", VaultSnippet.of("Una nota corta", "otra"))
        val long = "palabra ".repeat(60)
        val snippet = VaultSnippet.of(long, "inexistente")
        assertTrue(snippet.endsWith("…") && snippet.length <= VaultSnippet.LENGTH + 1)
        assertEquals("", VaultSnippet.of(null, "x"))
    }

    // ---- Guardar notas y carpetas

    private val saveNote = SaveNoteUseCase(vault, TEST_CLOCK)

    @Test
    fun `una nota nueva se guarda con su hora y las etiquetas limpias`() = runTest {
        val note = saveNote(null, parentId = null, title = " Escalas ", body = "Pentatónica", tags = listOf("#Musica", "musica", " ", "Ideas"))

        assertTrue(note.id != 0L)
        assertEquals("Escalas", note.title)
        assertEquals(listOf("Musica", "Ideas"), note.tags)
        assertEquals(TEST_NOW, note.updatedAt)
        assertEquals(note, vault.getById(note.id))
    }

    @Test
    fun `sin titulo la nota toma la primera linea del cuerpo`() = runTest {
        val note = saveNote(null, null, title = "  ", body = "\nLlamar a Marta\npor el viaje", tags = emptyList())

        assertEquals("Llamar a Marta", note.title)
    }

    @Test
    fun `guardar una nota existente la actualiza sin duplicarla`() = runTest {
        val first = saveNote(null, null, "Título", "uno", emptyList())

        val second = saveNote(first, parentId = null, title = "Título", body = "dos", tags = emptyList())

        assertEquals(first.id, second.id)
        assertEquals(1, vault.items.size)
        assertEquals("dos", vault.getById(first.id)?.markdownContent)
    }

    @Test
    fun `renombrar una carpeta conserva su contenido y su lugar`() = runTest {
        val folders = SaveFolderUseCase(vault, TEST_CLOCK)
        val parent = folders(null, null, "Música")!!
        val child = folders(null, parent.id, "Guitarra")!!
        saveNote(null, child.id, "Escalas", "x", emptyList())

        val renamed = folders(child, parentId = null, name = " Bajo ")!!

        assertEquals("Bajo", renamed.title)
        assertEquals(parent.id, renamed.parentId)
        assertEquals(listOf("Escalas"), vault.observeChildren(child.id).first().map { it.title })
    }

    @Test
    fun `una carpeta sin nombre no se crea`() = runTest {
        assertNull(SaveFolderUseCase(vault, TEST_CLOCK)(null, null, "   "))
        assertTrue(vault.items.isEmpty())
    }

    // ---- Borrar y deshacer

    @Test
    fun `borrar una nota la deja en la papelera y deshacer la devuelve con su id`() = runTest {
        val note = saveNote(null, null, "Para borrar", "texto", listOf("a"))

        DeleteVaultNodeUseCase(vault, trash)(note)
        assertTrue(vault.items.isEmpty())
        assertEquals(note, trash.lastDeleted.value)

        RestoreDeletedNoteUseCase(vault, trash)()

        assertEquals(note, vault.getById(note.id))
        assertNull(trash.lastDeleted.value)
    }

    @Test
    fun `si la carpeta de una nota borrada ya no existe, deshacer la devuelve a la raiz`() = runTest {
        val folders = SaveFolderUseCase(vault, TEST_CLOCK)
        val folder = folders(null, null, "Temporal")!!
        val note = saveNote(null, folder.id, "Dentro", "x", emptyList())
        val delete = DeleteVaultNodeUseCase(vault, trash)

        delete(note)
        delete(folder)
        RestoreDeletedNoteUseCase(vault, trash)()

        assertNull(vault.getById(note.id)?.parentId)
        assertTrue(vault.getById(note.id) != null)
    }

    @Test
    fun `borrar una carpeta se lleva todo su contenido y no deja nada que deshacer`() = runTest {
        val folders = SaveFolderUseCase(vault, TEST_CLOCK)
        val outer = folders(null, null, "Fuera")!!
        val inner = folders(null, outer.id, "Dentro")!!
        saveNote(null, inner.id, "Nota honda", "x", emptyList())
        saveNote(null, null, "Nota suelta", "y", emptyList())

        DeleteVaultNodeUseCase(vault, trash)(outer)

        assertEquals(listOf("Nota suelta"), vault.items.map { it.title })
        assertNull(trash.lastDeleted.value)
    }

    @Test
    fun `un aviso antiguo no vacia la papelera de uno mas reciente`() {
        val a = VaultNode(id = 1, title = "a", markdownContent = "")
        val b = VaultNode(id = 2, title = "b", markdownContent = "")
        trash.put(a)
        trash.put(b)

        trash.clear(a)

        assertEquals(b, trash.lastDeleted.value)
        assertFalse(trash.lastDeleted.value == null)
    }
}
