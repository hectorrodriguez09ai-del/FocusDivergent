package com.focusdivergent.app.data.repository

import android.app.Application
import com.focusdivergent.app.data.local.FocusDatabase
import com.focusdivergent.app.domain.model.VaultNode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class VaultRepositoryImplTest {

    private lateinit var db: FocusDatabase
    private lateinit var repository: VaultRepositoryImpl

    @Before
    fun setUp() {
        db = createTestDatabase()
        repository = VaultRepositoryImpl(db.vaultNodeDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `la fecha de la ultima modificacion se guarda y se recupera`() = runTest {
        val at = java.time.Instant.parse("2026-10-10T12:00:00Z")

        val id = repository.save(VaultNode(title = "Con fecha", markdownContent = "x", updatedAt = at))

        assertEquals(at, repository.getById(id)?.updatedAt)
    }

    @Test
    fun `observeFolders devuelve solo carpetas, de cualquier nivel, por orden alfabetico`() = runTest {
        val music = repository.save(VaultNode(title = "música"))
        repository.save(VaultNode(parentId = music, title = "Guitarra"))
        repository.save(VaultNode(title = "Zumo"))
        repository.save(VaultNode(parentId = music, title = "Una nota", markdownContent = "x"))

        assertEquals(listOf("Guitarra", "música", "Zumo"), repository.observeFolders().first().map { it.title })
    }

    @Test
    fun `observeTags junta las etiquetas de todas las notas, las mas usadas primero`() = runTest {
        repository.save(VaultNode(title = "A", markdownContent = "", tags = listOf("Ideas", "Música")))
        repository.save(VaultNode(title = "B", markdownContent = "", tags = listOf("música")))
        repository.save(VaultNode(title = "C", markdownContent = "", tags = listOf("Trabajo", "Música")))
        repository.save(VaultNode(title = "Sin etiquetas", markdownContent = ""))

        assertEquals(listOf("Música", "Ideas", "Trabajo"), repository.observeTags().first())
    }

    @Test
    fun `renombrar una carpeta no borra lo que contiene`() = runTest {
        val folder = repository.save(VaultNode(title = "Vieja"))
        repository.save(VaultNode(parentId = folder, title = "Dentro", markdownContent = "x"))

        repository.save(repository.getById(folder)!!.copy(title = "Nueva"))

        assertEquals(listOf("Dentro"), repository.observeChildren(folder).first().map { it.title })
    }

    @Test
    fun `save devuelve el id real al insertar y al actualizar, y conserva las etiquetas`() = runTest {
        val newId = repository.save(
            VaultNode(title = "Escalas", markdownContent = "Pentatónica", tags = listOf("Musica", "Ideas")),
        )

        val saved = repository.getById(newId)!!
        val updatedId = repository.save(saved.copy(markdownContent = "Modo dórico"))

        assertEquals(newId, saved.id)
        assertEquals(newId, updatedId)
        assertEquals(listOf("Musica", "Ideas"), repository.getById(newId)?.tags)
    }

    @Test
    fun `search encuentra por prefijo con la ultima palabra`() = runTest {
        val id = repository.save(VaultNode(title = "Escalas", markdownContent = "La escala pentatonica menor"))

        assertEquals(listOf(id), repository.search("penta").first().map { it.id })
        assertEquals(listOf(id), repository.search("escala penta").first().map { it.id })
    }

    @Test
    fun `search exige todas las palabras`() = runTest {
        repository.save(VaultNode(title = "Escalas", markdownContent = "La escala pentatonica menor"))

        assertTrue(repository.search("escala mayor").first().isEmpty())
    }

    @Test
    fun `search ignora acentos y mayusculas en los dos sentidos`() = runTest {
        val music = repository.save(VaultNode(title = "Teoría", markdownContent = "La escala PENTATÓNICA menor"))
        val folk = repository.save(VaultNode(title = "Ñandú", markdownContent = "Aves de la pampa", tags = listOf("Música")))

        // Consulta sin acento contra texto con acento y mayúsculas.
        assertEquals(listOf(music), repository.search("pentatonica").first().map { it.id })
        assertEquals(listOf(music), repository.search("teoria").first().map { it.id })
        assertEquals(listOf(folk), repository.search("nandu").first().map { it.id })
        assertEquals(listOf(folk), repository.search("musica").first().map { it.id })

        // Consulta con acento y mayúsculas contra el mismo texto.
        assertEquals(listOf(music), repository.search("PENTATÓNICA").first().map { it.id })
        assertEquals(listOf(folk), repository.search("ÑANDÚ").first().map { it.id })
        assertEquals(listOf(folk), repository.search("música").first().map { it.id })
    }

    @Test
    fun `search encuentra una nota editada con acentos nuevos`() = runTest {
        val id = repository.save(VaultNode(title = "Notas", markdownContent = "Modo dorico"))

        repository.save(repository.getById(id)!!.copy(markdownContent = "Modo dórico"))

        assertEquals(listOf(id), repository.search("dorico").first().map { it.id })
        assertEquals(listOf(id), repository.search("dórico").first().map { it.id })
    }

    @Test
    fun `search busca tambien en las etiquetas`() = runTest {
        val id = repository.save(VaultNode(title = "Enlaces", markdownContent = "Lista", tags = listOf("Documentos")))

        assertEquals(listOf(id), repository.search("documentos").first().map { it.id })
    }

    @Test
    fun `search con una consulta vacia devuelve una lista vacia`() = runTest {
        repository.save(VaultNode(title = "Escalas", markdownContent = "Pentatonica"))

        assertTrue(repository.search("").first().isEmpty())
        assertTrue(repository.search("   ").first().isEmpty())
        assertTrue(repository.search("\" -*").first().isEmpty())
    }

    @Test
    fun `search no falla con la sintaxis de FTS escrita por el usuario`() = runTest {
        repository.save(VaultNode(title = "Escalas", markdownContent = "Pentatonica and near or not"))
        val hostileInputs = listOf(
            "\"", "\"\"", "\"abc", "abc\"", "-", "-pentatonica", "*", "pent*", "(", ")", "(pentatonica",
            "AND", "OR", "NOT", "NEAR", "NEAR/2", "pentatonica AND", "OR pentatonica", "^pentatonica",
            "title:pentatonica", "content:", "a:b:c", "'", "%", "_", "\\", ";--", "pentatonica; DROP TABLE vault_nodes",
        )

        hostileInputs.forEach { input ->
            // Que el Flow termine sin lanzar es lo que se comprueba; el resultado concreto no importa.
            repository.search(input).first()
        }
    }

    @Test
    fun `los operadores de FTS se buscan como palabras literales`() = runTest {
        val id = repository.save(VaultNode(title = "Lógica", markdownContent = "uso de and y or"))

        assertEquals(listOf(id), repository.search("and or").first().map { it.id })
    }

    @Test
    fun `borrar una carpeta borra su contenido`() = runTest {
        val folderId = repository.save(VaultNode(title = "Ciberseguridad"))
        val noteId = repository.save(VaultNode(parentId = folderId, title = "Apuntes", markdownContent = "OWASP"))

        repository.delete(repository.getById(folderId)!!)

        assertNull(repository.getById(noteId))
        assertTrue(repository.observeChildren(null).first().isEmpty())
    }
}
