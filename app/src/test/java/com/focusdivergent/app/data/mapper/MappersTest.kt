package com.focusdivergent.app.data.mapper

import com.focusdivergent.app.data.local.entity.UserStatsEntity
import com.focusdivergent.app.data.local.entity.VaultNodeEntity
import com.focusdivergent.app.domain.model.AppLimit
import com.focusdivergent.app.domain.model.BrainDump
import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.model.PendingLimitChange
import com.focusdivergent.app.domain.model.Task
import com.focusdivergent.app.domain.model.TaskStatus
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.model.UserStats
import com.focusdivergent.app.domain.model.VaultNode
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MappersTest {

    @Test
    fun `Task conserva todos los campos en la ida y vuelta`() {
        val task = Task(
            id = 7,
            title = "Tomar medicación",
            type = TaskType.ROUTINE,
            energyLevel = EnergyLevel.LOW,
            status = TaskStatus.IN_PROGRESS,
            startTime = Instant.parse("2026-10-09T08:00:00Z"),
            endTime = Instant.parse("2026-10-09T08:15:00Z"),
            recurrenceRule = "FREQ=DAILY",
        )

        assertEquals(task, task.toEntity().toDomain())
    }

    @Test
    fun `Task sin horas guarda nulos y los recupera`() {
        val task = Task(title = "Lavar el coche", type = TaskType.TASK, energyLevel = EnergyLevel.HIGH)

        val entity = task.toEntity()

        assertEquals(null, entity.startTime)
        assertEquals(task, entity.toDomain())
    }

    @Test
    fun `Task guarda el instante como milisegundos epoch`() {
        val task = Task(
            title = "Cita médica",
            type = TaskType.OBLIGATION,
            energyLevel = EnergyLevel.MEDIUM,
            startTime = Instant.ofEpochMilli(1_760_000_000_123L),
        )

        assertEquals(1_760_000_000_123L, task.toEntity().startTime)
    }

    @Test
    fun `AppLimit conserva todos los campos en la ida y vuelta`() {
        val limit = AppLimit(packageName = "com.example.social", dailyMinutesAllowed = 45, isStrictlyBlocked = true, extensionsPerDay = 2, isLeisure = true)

        assertEquals(limit, limit.toEntity().toDomain())
    }

    @Test
    fun `un limite que no es de ocio sigue sin serlo tras la ida y vuelta`() {
        val limit = AppLimit(packageName = "com.example.social", dailyMinutesAllowed = 45)

        assertFalse(limit.toEntity().isLeisure)
        assertFalse(limit.toEntity().toDomain().isLeisure)
    }

    @Test
    fun `un cambio de limite pendiente conserva la marca de ocio`() {
        val newLimit = AppLimit(packageName = "com.example.social", dailyMinutesAllowed = 60, isLeisure = true)
        val change = PendingLimitChange("com.example.social", newLimit, Instant.parse("2026-10-11T00:00:00Z"))

        assertEquals(change, change.toEntity().toDomain())
        assertTrue(change.toEntity().isLeisure)
    }

    @Test
    fun `quitar el limite no deja marca de ocio en el cambio pendiente`() {
        val change = PendingLimitChange("com.example.social", null, Instant.parse("2026-10-11T00:00:00Z"))

        assertFalse(change.toEntity().isLeisure)
        assertEquals(change, change.toEntity().toDomain())
    }

    @Test
    fun `BrainDump conserva todos los campos en la ida y vuelta`() {
        val dump = BrainDump(
            id = 3,
            content = "Probar el sintetizador modular",
            timestamp = Instant.parse("2026-10-09T22:30:00Z"),
            isProcessed = true,
        )

        assertEquals(dump, dump.toEntity().toDomain())
    }

    @Test
    fun `VaultNode guarda las etiquetas con almohadilla y las devuelve sin ella`() {
        val note = VaultNode(
            id = 1,
            parentId = 9,
            title = "Escalas",
            markdownContent = "# Pentatónica",
            tags = listOf("Musica", "Ideas"),
        )

        val entity = note.toEntity()

        assertEquals("#Musica #Ideas", entity.tags)
        assertEquals(note, entity.toDomain())
    }

    @Test
    fun `VaultNode normaliza etiquetas con almohadilla, espacios o vacias`() {
        val note = VaultNode(title = "Notas", tags = listOf("#Ideas", " Docs ", "", "#"))

        assertEquals("#Ideas #Docs", note.toEntity().tags)
    }

    @Test
    fun `una entidad sin etiquetas da una lista vacia`() {
        val entity = VaultNodeEntity(title = "Carpeta", tags = "")

        assertEquals(emptyList<String>(), entity.toDomain().tags)
    }

    @Test
    fun `VaultNode sin contenido es una carpeta`() {
        assertTrue(VaultNode(title = "Ciberseguridad").isFolder)
        assertFalse(VaultNode(title = "Apuntes", markdownContent = "").isFolder)
    }

    @Test
    fun `UserStats se mapea sin el id de la fila`() {
        val entity = UserStatsEntity(bestStreak = 12)

        assertEquals(UserStats(bestStreak = 12), entity.toDomain())
    }
}
