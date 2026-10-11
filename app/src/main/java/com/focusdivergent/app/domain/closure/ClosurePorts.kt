package com.focusdivergent.app.domain.closure

import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.StateFlow

/** La configuración del Cierre y su cambio pendiente. Se guarda en el móvil. */
interface DayClosureSettings {

    val state: StateFlow<DayClosureState>

    fun update(state: DayClosureState)
}

/**
 * Lo que se anotó al cerrar un día.
 *
 * @property moved cuántas tareas pasaron al día siguiente.
 * @property respectedLimits en cuántas apps se respetó el límite del día.
 */
data class ClosureRecord(
    val day: LocalDate,
    val moved: Int,
    val respectedLimits: Int,
)

/** El último cierre hecho: es lo que impide cerrar dos veces el mismo día. */
interface DayClosureStore {

    val record: StateFlow<ClosureRecord?>

    fun save(record: ClosureRecord)
}

/**
 * Puerto hacia el sistema de alarmas: despierta a la app a la hora de cierre aunque no esté abierta. Hay una sola
 * alarma pendiente: programar sustituye a la anterior.
 */
interface ClosureAlarmScheduler {

    fun schedule(at: Instant)

    fun cancel()
}

/** Lo que se enseña al cerrar el día, fuera de la app. */
interface ClosureNotifier {

    /** Avisa de que el resumen de [day] está listo. [ideasWaiting] son las ideas sin procesar de la bandeja. */
    fun show(day: LocalDate, ideasWaiting: Int)
}

/** Lo que enseña el resumen de un día. Solo lo hecho: nada de lo que faltó. */
data class DaySummary(
    val day: LocalDate,
    val doneTitles: List<String>,
    val tasksDone: Int,
    val routinesDone: Int,
    val focusMinutes: Int,
    val xp: Int,
    val coins: Int,
    val moved: Int,
    val respectedLimits: Int,
    val ideasWaiting: Int,
) {
    val doneCount: Int get() = tasksDone + routinesDone

    /** No hay nada hecho que contar. */
    val isQuiet: Boolean get() = doneCount == 0 && focusMinutes == 0
}
