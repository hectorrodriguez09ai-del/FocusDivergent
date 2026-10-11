package com.focusdivergent.app.domain.policy

import com.focusdivergent.app.domain.model.AppLimit

/**
 * Cuánto tiempo extra se puede ganar con la fricción matemática (RF-1.3) y cuántas veces al día.
 *
 * El tope es deliberadamente duro: en perfiles con impulsividad, resolver problemas «por terquedad»
 * anularía la fricción. Cada límite lo fija con su nivel de fricción: 3 en el flexible, 0 en el firme.
 */
object ExtensionPolicy {

    /** Minutos de uso real que gana el usuario con cada ampliación. */
    const val MINUTES_PER_EXTENSION = 15

    private const val MILLIS_PER_MINUTE = 60_000L

    /**
     * Minutos que se suman al límite diario al conceder una ampliación.
     *
     * Mientras se ve la pantalla de pausa, la app bloqueada sigue abierta debajo y su tiempo sigue contando.
     * Si no se compensara, quien espera 5 minutos antes de resolver el reto recibiría solo 10 útiles (y quien
     * espera 15, ninguno). Por eso se suma, a los [MINUTES_PER_EXTENSION], lo que ya se haya pasado del
     * límite, redondeado hacia arriba: el usuario siempre gana 15 minutos de uso a partir de ahora.
     */
    fun minutesToGrant(limit: AppLimit, extraMinutesToday: Int, usedMillis: Long): Int {
        val allowedMillis = (limit.dailyMinutesAllowed + extraMinutesToday) * MILLIS_PER_MINUTE
        val overshootMillis = (usedMillis - allowedMillis).coerceAtLeast(0)
        val overshootMinutes = ((overshootMillis + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE).toInt()
        return MINUTES_PER_EXTENSION + overshootMinutes
    }

    /**
     * Ampliaciones que le quedan hoy a la app: las que permite su límite menos las ya usadas. Las apps con
     * bloqueo estricto, y las de nivel firme, nunca tienen ninguna.
     */
    fun remaining(limit: AppLimit, usedToday: Int): Int =
        if (limit.isStrictlyBlocked) 0 else (limit.extensionsPerDay - usedToday).coerceAtLeast(0)
}
