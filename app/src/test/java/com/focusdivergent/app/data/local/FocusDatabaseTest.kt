package com.focusdivergent.app.data.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusdivergent.app.data.local.entity.BrainDumpEntity
import com.focusdivergent.app.data.local.entity.TaskEntity
import com.focusdivergent.app.data.local.entity.UserStatsEntity
import com.focusdivergent.app.data.local.entity.VaultNodeEntity
import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.model.TaskType
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

/** Tests de los DAOs contra una base de datos Room en memoria (JVM, sin emulador). */
@RunWith(RobolectricTestRunner::class)
// Application "pelada" para no levantar Hilt; SDK fijado a uno que Robolectric soporta seguro.
@Config(sdk = [34], application = Application::class)
class FocusDatabaseTest {

    private lateinit var db: FocusDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FocusDatabase::class.java,
        )
            .addCallback(FocusDatabase.SeedCallback)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `observeByEnergy devuelve solo las tareas de ese nivel`() = runTest {
        val dao = db.taskDao()
        dao.upsert(TaskEntity(title = "Regar plantas", type = TaskType.TASK, energyLevel = EnergyLevel.LOW))
        dao.upsert(TaskEntity(title = "Declaración de la renta", type = TaskType.TASK, energyLevel = EnergyLevel.HIGH))

        val lowEnergy = dao.observeByEnergy(EnergyLevel.LOW).first()

        assertEquals(listOf("Regar plantas"), lowEnergy.map { it.title })
    }

    @Test
    fun `borrar una carpeta de la boveda borra tambien su contenido`() = runTest {
        val dao = db.vaultNodeDao()
        val folderId = dao.upsert(VaultNodeEntity(title = "Ciberseguridad"))
        val noteId = dao.upsert(
            VaultNodeEntity(parentId = folderId, title = "Apuntes", markdownContent = "# OWASP"),
        )

        dao.delete(dao.getById(folderId)!!)

        assertNull(dao.getById(noteId))
        assertTrue(dao.observeChildren(null).first().isEmpty())
    }

    @Test
    fun `search encuentra notas por contenido y refleja las ediciones`() = runTest {
        val dao = db.vaultNodeDao()
        val noteId = dao.upsert(
            VaultNodeEntity(title = "Escalas", markdownContent = "La escala pentatonica menor", tags = "#Musica"),
        )

        assertEquals(listOf(noteId), dao.search("pentatonica").first().map { it.id })
        assertEquals(listOf(noteId), dao.search("Musica").first().map { it.id })

        dao.upsert(dao.getById(noteId)!!.copy(markdownContent = "Modo dorico"))

        assertTrue(dao.search("pentatonica").first().isEmpty())
        assertEquals(listOf(noteId), dao.search("dorico").first().map { it.id })
    }

    @Test
    fun `user_stats nace con su fila unica y la mejor racha solo sube`() = runTest {
        val dao = db.userStatsDao()

        assertEquals(UserStatsEntity(), dao.observe().first())

        dao.raiseBestStreak(4)
        dao.raiseBestStreak(2)

        assertEquals(4, dao.observe().first()?.bestStreak)
    }

    @Test
    fun `una idea procesada sale de la bandeja de entrada`() = runTest {
        val dao = db.brainDumpDao()
        val id = dao.insert(BrainDumpEntity(content = "Mirar precios de sintetizadores", timestamp = 1_000L))

        dao.markProcessed(id)

        assertTrue(dao.observeUnprocessed().first().isEmpty())
    }
}
