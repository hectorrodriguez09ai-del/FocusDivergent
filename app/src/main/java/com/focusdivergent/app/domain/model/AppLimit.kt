package com.focusdivergent.app.domain.model

/**
 * Límite diario de una aplicación (RF-1.1), identificada por su nombre de paquete.
 *
 * Con [isStrictlyBlocked] la app queda bloqueada siempre, sin depender del tiempo consumido.
 * [extensionsPerDay] es cuántas veces al día se puede ganar tiempo extra resolviendo el reto matemático:
 * [DEFAULT_EXTENSIONS_PER_DAY] en el nivel flexible y 0 en el firme. Mira también [friction].
 *
 * Con [isLeisure] la app es «de ocio»: el Cierre de Jornada la deja libre de noche, entre la hora de cierre y la de
 * la mañana. Solo se levanta este límite; el resto de avisos y comprobaciones siguen igual.
 */
data class AppLimit(
    val packageName: String,
    val dailyMinutesAllowed: Int,
    val isStrictlyBlocked: Boolean = false,
    val extensionsPerDay: Int = DEFAULT_EXTENSIONS_PER_DAY,
    val isLeisure: Boolean = false,
) {
    companion object {
        /**
         * Tope duro de ampliaciones al día (3 de 15 minutos, 45 en total). En perfiles con impulsividad,
         * resolver problemas «por terquedad» anularía la fricción.
         */
        const val DEFAULT_EXTENSIONS_PER_DAY = 3
    }
}
