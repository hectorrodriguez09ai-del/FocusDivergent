package com.focusdivergent.app.domain.tasks

import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.model.Task
import com.focusdivergent.app.domain.model.TaskStatus
import com.focusdivergent.app.domain.model.TaskType
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * Qué se le propone a quien no sabe por dónde empezar («Elige por mí», RF-3.3).
 *
 * La idea es quitar la decisión: se devuelve **una sola** actividad entre las que caben ahora. Todas las funciones
 * son puras; el azar entra como parámetro para poder probarlo.
 */
object TaskChooser {

    /** El resultado: la elegida y cuántas otras habría como alternativa (sin contar la elegida). */
    data class Pick(val task: Task, val othersLeft: Int)

    // Lo que ya toca hoy pesa más que lo que no tiene fecha; y lo que pide justo la energía de hoy, más aún.
    private const val WEIGHT_DUE = 3
    private const val WEIGHT_UNDATED = 1
    private const val WEIGHT_EXACT_ENERGY = 2

    /**
     * Las actividades que se pueden proponer ahora, cada una con su peso.
     *
     * - **Tareas** pendientes (no hechas ni ya en marcha) sin fecha, o con fecha de hoy o anterior.
     * - **Rutinas** que tocan hoy y aún no se han hecho hoy.
     * - Las **Obligaciones** nunca: tienen su hora y su aviso.
     * - La energía que piden no supera la de hoy.
     * - Si hay tiempo límite, la duración prevista cabe; las que no tienen duración siempre caben.
     *
     * @param availableMinutes tiempo disponible, o nulo si no hay prisa.
     * @param exclude actividades que ya se han enseñado en esta ronda.
     */
    fun candidates(
        tasks: List<Task>,
        routinesDoneToday: Set<Long>,
        today: LocalDate,
        zone: ZoneId,
        energy: EnergyLevel,
        availableMinutes: Int?,
        exclude: Set<Long> = emptySet(),
    ): List<Pair<Task, Int>> = tasks.mapNotNull { task ->
        if (task.id in exclude) return@mapNotNull null
        if (task.energyLevel.value > energy.value) return@mapNotNull null
        val minutes = task.estimatedMinutes
        if (availableMinutes != null && minutes != null && minutes > availableMinutes) return@mapNotNull null

        val base = when (task.type) {
            TaskType.OBLIGATION -> return@mapNotNull null
            TaskType.ROUTINE ->
                if (Recurrence.parse(task.recurrenceRule).occursOn(today) && task.id !in routinesDoneToday) WEIGHT_DUE else return@mapNotNull null
            TaskType.TASK -> {
                if (task.status != TaskStatus.PENDING) return@mapNotNull null
                val date = task.startTime?.atZone(zone)?.toLocalDate()
                when {
                    date == null -> WEIGHT_UNDATED
                    date <= today -> WEIGHT_DUE
                    else -> return@mapNotNull null
                }
            }
        }
        task to base + if (task.energyLevel == energy) WEIGHT_EXACT_ENERGY else 0
    }

    /**
     * Elige una actividad al azar entre las que caben, con más probabilidad para las de más peso.
     * Devuelve nulo si no hay ninguna.
     */
    fun pick(
        tasks: List<Task>,
        routinesDoneToday: Set<Long>,
        today: LocalDate,
        zone: ZoneId,
        energy: EnergyLevel,
        availableMinutes: Int?,
        random: Random,
        exclude: Set<Long> = emptySet(),
    ): Pick? {
        val candidates = candidates(tasks, routinesDoneToday, today, zone, energy, availableMinutes, exclude)
        if (candidates.isEmpty()) return null

        var ticket = random.nextInt(candidates.sumOf { it.second })
        val chosen = candidates.first { (_, weight) ->
            ticket -= weight
            ticket < 0
        }
        return Pick(task = chosen.first, othersLeft = candidates.size - 1)
    }
}
