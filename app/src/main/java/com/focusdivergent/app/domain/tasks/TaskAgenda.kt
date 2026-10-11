package com.focusdivergent.app.domain.tasks

import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.model.Task
import com.focusdivergent.app.domain.model.TaskStatus
import com.focusdivergent.app.domain.model.TaskType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Bloques de la lista de actividades. No existe «atrasadas» a propósito (RNF-3). */
enum class TaskSection { TODAY, UPCOMING, SOMEDAY, DONE_TODAY }

/**
 * Una actividad colocada en la lista.
 *
 * @property isDone hecha (una rutina, hecha ese día).
 * @property date día en que toca, si tiene alguno: para una rutina, su próxima ocurrencia.
 * @property time hora a la que toca, o nulo si no tiene hora.
 */
data class AgendaItem(
    val task: Task,
    val isDone: Boolean,
    val date: LocalDate?,
    val time: LocalTime?,
)

/** Filtro de la lista. Un campo nulo significa «todo». */
data class TaskFilter(
    val energy: EnergyLevel? = null,
    val type: TaskType? = null,
) {
    val isActive: Boolean get() = energy != null || type != null

    fun matches(task: Task): Boolean =
        (energy == null || task.energyLevel == energy) && (type == null || task.type == type)
}

/** Reglas para decidir qué actividades se ven y dónde. Funciones puras: no leen reloj ni base de datos. */
object TaskAgenda {

    /**
     * Vista de hoy: todo lo pendiente, repartido en bloques.
     *
     * - **Hoy**: lo que tiene fecha de hoy o anterior y sigue pendiente, y las rutinas que tocan hoy. Lo de días
     *   pasados aparece aquí sin ninguna marca: sigue siendo algo que se puede hacer hoy.
     * - **Próximamente**: lo que tiene fecha futura y las rutinas que hoy no tocan (con su próximo día).
     * - **Sin fecha**: tareas sin día asignado.
     * - **Hechas hoy**: lo completado hoy. Lo completado otros días no se muestra.
     *
     * Los bloques vacíos no aparecen en el resultado.
     */
    fun overview(
        tasks: List<Task>,
        routinesDoneToday: Set<Long>,
        today: LocalDate,
        zone: ZoneId,
        filter: TaskFilter = TaskFilter(),
    ): Map<TaskSection, List<AgendaItem>> {
        val sections = TaskSection.entries.associateWith { mutableListOf<AgendaItem>() }

        for (task in tasks) {
            if (!filter.matches(task)) continue
            val start = task.startTime?.atZone(zone)
            val time = start?.toLocalTime()?.takeUnless { hidesTime(task, it) }

            if (task.type == TaskType.ROUTINE) {
                val recurrence = Recurrence.parse(task.recurrenceRule)
                if (recurrence.occursOn(today)) {
                    val done = task.id in routinesDoneToday
                    sections.getValue(if (done) TaskSection.DONE_TODAY else TaskSection.TODAY) +=
                        AgendaItem(task, done, today, time)
                } else {
                    sections.getValue(TaskSection.UPCOMING) +=
                        AgendaItem(task, false, recurrence.nextOccurrence(today.plusDays(1)), time)
                }
                continue
            }

            val date = start?.toLocalDate()
            when {
                task.status == TaskStatus.DONE -> {
                    if (task.completedAt?.atZone(zone)?.toLocalDate() == today) {
                        sections.getValue(TaskSection.DONE_TODAY) += AgendaItem(task, true, date, time)
                    }
                }
                date == null -> sections.getValue(TaskSection.SOMEDAY) += AgendaItem(task, false, null, null)
                date <= today -> sections.getValue(TaskSection.TODAY) += AgendaItem(task, false, date, time)
                else -> sections.getValue(TaskSection.UPCOMING) += AgendaItem(task, false, date, time)
            }
        }

        sections.getValue(TaskSection.TODAY).sortWith(BY_TIME)
        sections.getValue(TaskSection.UPCOMING).sortWith(compareBy<AgendaItem> { it.date }.then(BY_TIME))
        sections.getValue(TaskSection.SOMEDAY).sortBy { it.task.id }
        sections.getValue(TaskSection.DONE_TODAY).sortWith(BY_TIME)
        return sections.filterValues { it.isNotEmpty() }
    }

    /**
     * Agenda de un día concreto: lo que tiene fecha de ese día (hecho o no) y las rutinas que tocan, por hora.
     * [routinesDoneThatDay] son las rutinas marcadas ese día.
     */
    fun forDay(
        tasks: List<Task>,
        routinesDoneThatDay: Set<Long>,
        day: LocalDate,
        zone: ZoneId,
        filter: TaskFilter = TaskFilter(),
    ): List<AgendaItem> = tasks.mapNotNull { task ->
        if (!filter.matches(task)) return@mapNotNull null
        val start = task.startTime?.atZone(zone)
        val time = start?.toLocalTime()?.takeUnless { hidesTime(task, it) }
        when {
            task.type == TaskType.ROUTINE ->
                if (Recurrence.parse(task.recurrenceRule).occursOn(day)) {
                    AgendaItem(task, task.id in routinesDoneThatDay, day, time)
                } else {
                    null
                }
            start?.toLocalDate() == day -> AgendaItem(task, task.status == TaskStatus.DONE, day, time)
            else -> null
        }
    }.sortedWith(BY_TIME)

    /** Los siete días de la semana que contiene [day], empezando por [firstDayOfWeek]. */
    fun weekOf(day: LocalDate, firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): List<LocalDate> {
        val first = day.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        return List(7) { first.plusDays(it.toLong()) }
    }

    // Una tarea con solo día se guarda a medianoche: esa hora no es real y no se enseña.
    private fun hidesTime(task: Task, time: LocalTime) = time == LocalTime.MIDNIGHT && task.type != TaskType.OBLIGATION

    // Primero lo que tiene hora, en orden; después lo que no, en orden de creación.
    private val BY_TIME: Comparator<AgendaItem> =
        compareBy<AgendaItem> { it.time == null }.thenBy { it.time }.thenBy { it.task.id }
}
