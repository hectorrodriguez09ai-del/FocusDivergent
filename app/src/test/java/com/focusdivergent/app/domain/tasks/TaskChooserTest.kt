package com.focusdivergent.app.domain.tasks

import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.model.Task
import com.focusdivergent.app.domain.model.TaskStatus
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.usecase.TEST_TODAY
import com.focusdivergent.app.domain.usecase.task
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** «Ahora» es el sábado 10 de octubre de 2026 a las 12:00 UTC. */
class TaskChooserTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private val day = 24 * 60L

    private fun candidates(
        vararg tasks: Task,
        energy: EnergyLevel = EnergyLevel.HIGH,
        minutes: Int? = null,
        routinesDone: Set<Long> = emptySet(),
        exclude: Set<Long> = emptySet(),
    ) = TaskChooser.candidates(tasks.toList(), routinesDone, TEST_TODAY, utc, energy, minutes, exclude)

    private fun ids(vararg tasks: Task, energy: EnergyLevel = EnergyLevel.HIGH, minutes: Int? = null) =
        candidates(*tasks, energy = energy, minutes = minutes).map { it.first.id }.toSet()

    // ---- Qué entra

    @Test
    fun `entran las tareas pendientes sin fecha, de hoy y de dias anteriores`() {
        val result = ids(
            task(1),                              // sin fecha
            task(2, startsInMinutes = 60),        // hoy
            task(3, startsInMinutes = -3 * day),  // de hace días: sigue siendo algo que se puede hacer hoy
        )

        assertEquals(setOf(1L, 2L, 3L), result)
    }

    @Test
    fun `no entran las de dias futuros`() {
        assertEquals(emptySet<Long>(), ids(task(1, startsInMinutes = 2 * day)))
    }

    @Test
    fun `no entran las hechas ni las que ya estan en marcha`() {
        assertEquals(
            emptySet<Long>(),
            ids(task(1, status = TaskStatus.DONE), task(2, status = TaskStatus.IN_PROGRESS)),
        )
    }

    @Test
    fun `las obligaciones no entran nunca, tienen su hora y su aviso`() {
        assertEquals(emptySet<Long>(), ids(task(1, type = TaskType.OBLIGATION, startsInMinutes = 60)))
    }

    @Test
    fun `una rutina que toca hoy y no se ha hecho entra`() {
        val daily = task(1, type = TaskType.ROUTINE, recurrenceRule = "FREQ=DAILY")

        assertEquals(setOf(1L), ids(daily))
    }

    @Test
    fun `una rutina ya hecha hoy no entra, ni una que hoy no toca`() {
        val done = task(1, type = TaskType.ROUTINE, recurrenceRule = "FREQ=DAILY")
        val mondays = task(2, type = TaskType.ROUTINE, recurrenceRule = "FREQ=WEEKLY;BYDAY=MO")

        val result = candidates(done, mondays, routinesDone = setOf(1L)).map { it.first.id }

        assertEquals(emptyList<Long>(), result)
    }

    // ---- Energía y tiempo

    @Test
    fun `solo entran las que piden la energia de hoy o menos`() {
        val low = task(1, energy = EnergyLevel.LOW)
        val medium = task(2, energy = EnergyLevel.MEDIUM)
        val high = task(3, energy = EnergyLevel.HIGH)

        assertEquals(setOf(1L), ids(low, medium, high, energy = EnergyLevel.LOW))
        assertEquals(setOf(1L, 2L), ids(low, medium, high, energy = EnergyLevel.MEDIUM))
        assertEquals(setOf(1L, 2L, 3L), ids(low, medium, high, energy = EnergyLevel.HIGH))
    }

    @Test
    fun `con tiempo limitado solo entran las que caben, y las que no tienen duracion siempre caben`() {
        val short = task(1, estimatedMinutes = 10)
        val exact = task(2, estimatedMinutes = 15)
        val long = task(3, estimatedMinutes = 16)
        val unknown = task(4)

        assertEquals(setOf(1L, 2L, 4L), ids(short, exact, long, unknown, minutes = 15))
        assertEquals(setOf(1L, 4L), ids(short, exact, long, unknown, minutes = 10))
    }

    @Test
    fun `sin prisa entran todas, dure lo que dure`() {
        assertEquals(setOf(1L, 2L), ids(task(1, estimatedMinutes = 240), task(2), minutes = null))
    }

    @Test
    fun `las ya enseñadas se excluyen`() {
        val result = candidates(task(1), task(2), exclude = setOf(1L)).map { it.first.id }

        assertEquals(listOf(2L), result)
    }

    // ---- Pesos

    @Test
    fun `lo que ya toca pesa mas que lo que no tiene fecha, y la energia exacta suma mas`() {
        val tasks = arrayOf(
            task(1, startsInMinutes = 60, energy = EnergyLevel.MEDIUM),                       // hoy, energía exacta
            task(2, startsInMinutes = 60, energy = EnergyLevel.LOW),                          // hoy, energía menor
            task(3, energy = EnergyLevel.MEDIUM),                                             // sin fecha, exacta
            task(4, energy = EnergyLevel.LOW),                                                // sin fecha, menor
            task(5, type = TaskType.ROUTINE, energy = EnergyLevel.MEDIUM),                    // rutina de hoy, exacta
        )

        val weights = candidates(*tasks, energy = EnergyLevel.MEDIUM).associate { it.first.id to it.second }

        assertEquals(mapOf(1L to 5, 2L to 3, 3L to 3, 4L to 1, 5L to 5), weights)
    }

    // ---- Elección

    @Test
    fun `sin candidatas no se propone nada`() {
        assertNull(TaskChooser.pick(emptyList(), emptySet(), TEST_TODAY, utc, EnergyLevel.HIGH, null, Random(1)))
        assertNull(
            TaskChooser.pick(listOf(task(1, energy = EnergyLevel.HIGH)), emptySet(), TEST_TODAY, utc, EnergyLevel.LOW, null, Random(1)),
        )
    }

    @Test
    fun `con una sola candidata se propone esa y no quedan alternativas`() {
        val pick = TaskChooser.pick(listOf(task(7)), emptySet(), TEST_TODAY, utc, EnergyLevel.HIGH, null, Random(1))!!

        assertEquals(7L, pick.task.id)
        assertEquals(0, pick.othersLeft)
    }

    @Test
    fun `se cuenta cuantas alternativas quedan sin contar la elegida`() {
        val tasks = listOf(task(1), task(2), task(3), task(4))

        val pick = TaskChooser.pick(tasks, emptySet(), TEST_TODAY, utc, EnergyLevel.HIGH, null, Random(3))!!

        assertEquals(3, pick.othersLeft)
    }

    @Test
    fun `la eleccion respeta los pesos pero cualquiera puede salir`() {
        // Una pesa 5 (de hoy, energía exacta) y la otra 1 (sin fecha, energía menor): salen ~83 % y ~17 %.
        val heavy = task(1, startsInMinutes = 60, energy = EnergyLevel.MEDIUM)
        val light = task(2, energy = EnergyLevel.LOW)
        val random = Random(2026)

        val picks = List(6_000) {
            TaskChooser.pick(listOf(heavy, light), emptySet(), TEST_TODAY, utc, EnergyLevel.MEDIUM, null, random)!!.task.id
        }

        val heavyShare = picks.count { it == 1L } / picks.size.toDouble()
        assertTrue("la pesada debería salir ~83 %: $heavyShare", heavyShare in 0.78..0.88)
        assertTrue("la ligera también sale alguna vez", picks.contains(2L))
    }

    @Test
    fun `con la misma semilla la eleccion es siempre la misma`() {
        val tasks = List(8) { task(it + 1L) }

        fun run() = List(5) { TaskChooser.pick(tasks, emptySet(), TEST_TODAY, utc, EnergyLevel.HIGH, null, Random(99))!!.task.id }

        assertEquals(run(), run())
    }

    @Test
    fun `nunca propone una actividad excluida`() {
        val tasks = listOf(task(1), task(2), task(3))
        val random = Random(5)

        repeat(200) {
            val pick = TaskChooser.pick(tasks, emptySet(), TEST_TODAY, utc, EnergyLevel.HIGH, null, random, exclude = setOf(1L, 2L))
            assertEquals(3L, pick?.task?.id)
        }
        assertNotNull(TaskChooser.pick(tasks, emptySet(), TEST_TODAY, utc, EnergyLevel.HIGH, null, random, exclude = setOf(1L)))
    }

    @Test
    fun `el dia se calcula en la zona del usuario`() {
        // A las 23:30 UTC del sábado ya es domingo en Madrid: la fecha de una tarea depende de la zona del usuario.
        val saturday = task(1, startsInMinutes = 11 * 60 + 30)
        val sunday = task(2, startsInMinutes = 12 * 60 + 30)
        val madrid = ZoneId.of("Europe/Madrid")

        val utcResult = TaskChooser.candidates(listOf(saturday, sunday), emptySet(), TEST_TODAY, utc, EnergyLevel.HIGH, null)
        val madridResult = TaskChooser.candidates(listOf(saturday, sunday), emptySet(), TEST_TODAY, madrid, EnergyLevel.HIGH, null)

        assertEquals(setOf(1L), utcResult.map { it.first.id }.toSet())
        // En Madrid ambas son del domingo, futuras respecto a «hoy» = sábado: ninguna entra.
        assertEquals(emptySet<Long>(), madridResult.map { it.first.id }.toSet())
    }
}
