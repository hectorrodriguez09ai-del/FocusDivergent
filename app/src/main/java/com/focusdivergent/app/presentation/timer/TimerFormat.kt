package com.focusdivergent.app.presentation.timer

import java.util.Locale

/** «12:34», o «1:05:09» si pasa de la hora. Los segundos negativos se enseñan como cero. */
internal fun formatClock(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val rest = seconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, rest)
    }
}

/** Minutos que se dicen en voz alta (redondeando hacia arriba): «quedan 13 minutos» cuando faltan 12 min y 5 s. */
internal fun spokenMinutes(totalSeconds: Long): Int = ((totalSeconds.coerceAtLeast(0) + 59) / 60).toInt()
