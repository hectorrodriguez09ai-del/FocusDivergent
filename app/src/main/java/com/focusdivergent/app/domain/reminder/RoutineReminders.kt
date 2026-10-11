package com.focusdivergent.app.domain.reminder

import com.focusdivergent.app.domain.model.Task
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.tasks.Recurrence
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Cuándo avisar de una Rutina con hora (SRD §3.2). Funciones puras.
 *
 * El aviso es amable por diseño: una sola notificación a la hora de la rutina, que no se repite ni sube de tono. Si
 * el aviso llega tarde (alarma retrasada, móvil apagado) y ya pasó demasiado rato, se deja pasar sin más.
 */
object RoutineReminderPolicy {

    /** Hasta cuánto después de su hora aún tiene sentido avisar. Cubre el retraso medido de las alarmas aproximadas. */
    val LATE_WINDOW: Duration = Duration.ofMinutes(30)

    /** Cuánto tarda en volver a avisar «Más tarde». Una sola vez. */
    val SNOOZE: Duration = Duration.ofHours(1)

    /** La hora del día a la que avisa la rutina, o nulo si no tiene (la medianoche exacta significa «sin hora»). */
    fun timeOf(task: Task, zone: ZoneId): LocalTime? {
        if (task.type != TaskType.ROUTINE) return null
        return task.startTime?.atZone(zone)?.toLocalTime()?.takeUnless { it == LocalTime.MIDNIGHT }
    }

    private fun occursOn(task: Task, date: LocalDate) = Recurrence.parse(task.recurrenceRule).occursOn(date)

    /** El primer aviso de cualquier rutina estrictamente después de [after], o nulo si no hay ninguna con hora. */
    fun next(routines: List<Task>, after: Instant, zone: ZoneId): Instant? {
        val today = after.atZone(zone).toLocalDate()
        return routines.mapNotNull { task ->
            val time = timeOf(task, zone) ?: return@mapNotNull null
            // Una semana basta para encontrar el próximo día en que toca (como mucho es una vez por semana).
            (0L..7L).asSequence()
                .map { today.plusDays(it) }
                .filter { occursOn(task, it) }
                .map { it.atTime(time).atZone(zone).toInstant() }
                .firstOrNull { it > after }
        }.minOrNull()
    }

    /**
     * Las rutinas a las que toca avisar ahora: su hora de hoy ya llegó y no han pasado más de [LATE_WINDOW]. Quien
     * llama descarta las que ya están hechas hoy o ya se avisaron.
     */
    fun due(routines: List<Task>, now: Instant, zone: ZoneId): List<Task> {
        val today = now.atZone(zone).toLocalDate()
        return routines.filter { task ->
            val time = timeOf(task, zone) ?: return@filter false
            if (!occursOn(task, today)) return@filter false
            val at = today.atTime(time).atZone(zone).toInstant()
            at <= now && now < at + LATE_WINDOW
        }
    }
}

/** Puerto hacia el sistema de alarmas para los avisos de rutinas. Hay una alarma general y una por rutina para «Más tarde». */
interface RoutineReminderAlarms {

    /** La alarma general: se despierta en el próximo aviso. Sustituye a la anterior. */
    fun schedule(at: Instant)

    fun cancel()

    /** El «Más tarde» de una rutina. Una por rutina; sustituye a la anterior de esa rutina. */
    fun scheduleSnooze(taskId: Long, at: Instant)

    fun cancelSnooze(taskId: Long)
}

/** El aviso en sí, fuera de la app. */
interface RoutineReminderNotifier {

    /** Muestra el aviso de [task]. Con [canSnooze] ofrece «Más tarde»; si no, solo «Hecha». */
    fun show(task: Task, canSnooze: Boolean)

    fun dismiss(taskId: Long)
}

/** Qué rutinas ya se avisaron en qué día: es lo que impide avisar dos veces. */
interface RoutineReminderLog {

    fun wasNotified(taskId: Long, day: LocalDate): Boolean

    /** Anota el aviso y olvida los de días anteriores. */
    fun markNotified(taskId: Long, day: LocalDate)
}
