package com.focusdivergent.app.domain.closure

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Cómo está configurado el Cierre de Jornada (RF-6.2).
 *
 * @property closeAt a qué hora se cierra el día: resumen, traslado de lo pendiente y apps de ocio libres.
 * @property liftUntil hasta qué hora de la mañana siguiente siguen libres las apps de ocio.
 */
data class DayClosureConfig(
    val enabled: Boolean = true,
    val closeAt: LocalTime = DEFAULT_CLOSE_AT,
    val liftUntil: LocalTime = DEFAULT_LIFT_UNTIL,
) {
    companion object {
        val DEFAULT_CLOSE_AT: LocalTime = LocalTime.of(21, 30)
        val DEFAULT_LIFT_UNTIL: LocalTime = LocalTime.of(6, 0)
    }
}

/** Lo que se puede elegir, para que la pantalla ofrezca atajos en vez de pedir una hora cualquiera. */
object ClosureOptions {
    val CLOSE_TIMES: List<LocalTime> = listOf(
        LocalTime.of(20, 0), LocalTime.of(20, 30), LocalTime.of(21, 0), LocalTime.of(21, 30),
        LocalTime.of(22, 0), LocalTime.of(22, 30), LocalTime.of(23, 0),
    )
    val LIFT_TIMES: List<LocalTime> = (4..8).map { LocalTime.of(it, 0) }
}

/** Un cambio de configuración que espera a medianoche. */
data class PendingClosureConfig(val config: DayClosureConfig, val appliesAt: Instant)

/**
 * La configuración que rige y, si lo hay, el cambio que entrará en vigor a medianoche. Los cambios solo entran en
 * vigor a medianoche exacta, así que [configAt] vale para saber cómo estará cualquier día futuro.
 */
data class DayClosureState(
    val effective: DayClosureConfig = DayClosureConfig(),
    val pending: PendingClosureConfig? = null,
) {
    /** La configuración que rige en el instante [at]: la pendiente, si su hora ya ha llegado. */
    fun configAt(at: Instant): DayClosureConfig = pending?.takeIf { at >= it.appliesAt }?.config ?: effective
}

/**
 * Regla de los cambios del Cierre, la misma que la de los límites: **endurecer es inmediato, relajar espera a
 * medianoche**. Relaja todo lo que da más margen a las apps de ocio: activar el cierre, cerrar antes o dejar la noche
 * libre más tiempo. Sin esto, activar el cierre sería la puerta trasera de todos los límites.
 */
object ClosurePolicy {

    /** Lo que quedaría de [desired] si solo se aplicara lo que endurece respecto a [current]. */
    fun tightenedPart(current: DayClosureConfig, desired: DayClosureConfig) = DayClosureConfig(
        enabled = current.enabled && desired.enabled,
        closeAt = maxOf(current.closeAt, desired.closeAt),
        liftUntil = minOf(current.liftUntil, desired.liftUntil),
    )

    /** `true` si [desired] da más margen que [current] en algo. */
    fun isRelaxation(current: DayClosureConfig, desired: DayClosureConfig): Boolean =
        tightenedPart(current, desired) != desired

    /**
     * El estado que resulta de pedir [desired] estando [current] en vigor: lo que endurece rige ya y el resto espera
     * a [midnight]. Pedir lo que ya rige borra cualquier cambio pendiente.
     */
    fun plan(current: DayClosureConfig, desired: DayClosureConfig, midnight: Instant): DayClosureState {
        val now = tightenedPart(current, desired)
        return if (now == desired) {
            DayClosureState(effective = desired)
        } else {
            DayClosureState(effective = now, pending = PendingClosureConfig(desired, midnight))
        }
    }
}

/** Cuándo está abierta la noche del cierre y qué día toca cerrar. Todo se deduce de la hora, sin contadores. */
object ClosureWindow {

    /** `true` si a la hora [at] las apps de ocio están libres: desde la hora de cierre hasta la de la mañana. */
    fun isOpen(config: DayClosureConfig, at: Instant, zone: ZoneId): Boolean {
        if (!config.enabled) return false
        val time = at.atZone(zone).toLocalTime()
        return time >= config.closeAt || time < config.liftUntil
    }

    /**
     * El día que toca cerrar ahora, o nulo si no toca. Es el de hoy si ya pasó la hora de cierre, y el de ayer si
     * aún estamos en la madrugada de su noche (por si el cierre no llegó a hacerse). De día no se cierra nada.
     */
    fun dueDay(config: DayClosureConfig, at: Instant, zone: ZoneId): LocalDate? {
        if (!config.enabled) return null
        val local = at.atZone(zone)
        val time = local.toLocalTime()
        return when {
            time >= config.closeAt -> local.toLocalDate()
            time < config.liftUntil -> local.toLocalDate().minusDays(1)
            else -> null
        }
    }
}
