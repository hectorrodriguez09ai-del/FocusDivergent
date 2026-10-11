package com.focusdivergent.app.data.usage

import com.focusdivergent.app.domain.model.UsageSnapshot
import com.focusdivergent.app.domain.policy.RealityCheckPolicy

/** Evento de uso reducido a lo que importa: una actividad pasa a primer plano o sale de él. */
internal data class RawUsageEvent(
    val packageName: String,
    val className: String,
    val isResume: Boolean,
    val timestampMillis: Long,
)

/**
 * Calcula el tiempo en primer plano de cada app a partir de los eventos del sistema.
 *
 * Se suman los tramos entre cada "reanudada" y su "pausada". Las actividades que siguen abiertas
 * cuentan hasta [nowMillis] y la más reciente de ellas es la app en primer plano. Un "pausada" sin
 * su "reanudada" significa que la actividad ya estaba abierta al empezar la ventana
 * ([windowStartMillis]), así que cuenta desde ahí.
 *
 * Además calcula cuánto lleva seguido la app en primer plano (ver [continuousSessionMillis]).
 *
 * Se parte de eventos y no de `queryUsageStats` porque este último agrega por intervalos y puede
 * dar cifras de "hoy" desfasadas.
 */
internal fun computeUsageSnapshot(
    events: List<RawUsageEvent>,
    windowStartMillis: Long,
    nowMillis: Long,
): UsageSnapshot {
    val totals = mutableMapOf<String, Long>()
    val intervals = mutableListOf<UsageInterval>()
    val openSince = LinkedHashMap<Pair<String, String>, Long>()

    fun addTime(packageName: String, from: Long, to: Long) {
        if (to <= from) return
        totals.merge(packageName, to - from, Long::plus)
        intervals += UsageInterval(packageName, from, to)
    }

    for (event in events.sortedBy { it.timestampMillis }) {
        val activity = event.packageName to event.className
        if (event.isResume) {
            // Una segunda "reanudada" sin pausa intermedia no reinicia el tramo.
            openSince.putIfAbsent(activity, event.timestampMillis)
        } else {
            addTime(event.packageName, openSince.remove(activity) ?: windowStartMillis, event.timestampMillis)
        }
    }

    for ((activity, since) in openSince) addTime(activity.first, since, nowMillis)

    val foreground = openSince.entries.maxByOrNull { it.value }?.key?.first
    val session = foreground?.let { continuousSessionMillis(intervals.filter { i -> i.packageName == it }, nowMillis) } ?: 0L
    return UsageSnapshot(foregroundPackage = foreground, usedMillisByPackage = totals, foregroundSessionMillis = session)
}

private class UsageInterval(val packageName: String, val from: Long, val to: Long)

/**
 * Tiempo seguido que lleva una app en uso hasta [nowMillis], a partir de los tramos en que estuvo en primer plano.
 *
 * Dos tramos pertenecen a la misma sesión si entre uno y otro pasaron como mucho
 * [RealityCheckPolicy.GAP_TOLERANCE_MILLIS]: mirar una notificación o apagar y encender la pantalla no reinicia la
 * cuenta, pero salir a otra app durante más tiempo sí. Los tramos que se solapan (varias actividades de la misma
 * app) también se unen. Devuelve 0 si la app no tiene ningún tramo.
 */
private fun continuousSessionMillis(intervals: List<UsageInterval>, nowMillis: Long): Long {
    val ordered = intervals.sortedByDescending { it.from }
    val latest = ordered.firstOrNull() ?: return 0L

    var sessionStart = latest.from
    for (interval in ordered.drop(1)) {
        if (interval.to >= sessionStart - RealityCheckPolicy.GAP_TOLERANCE_MILLIS) {
            sessionStart = minOf(sessionStart, interval.from)
        }
    }
    return nowMillis - sessionStart
}
