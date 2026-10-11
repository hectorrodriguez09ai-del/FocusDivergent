package com.focusdivergent.app.domain.model

/**
 * Cuánto cuesta saltarse un límite. Es lo que el usuario elige en pantalla; por debajo se traduce a
 * [AppLimit.isStrictlyBlocked] y [AppLimit.extensionsPerDay].
 */
enum class FrictionLevel {
    /** Al llegar al límite se puede ganar tiempo extra con el reto matemático, hasta 3 veces al día. */
    FLEXIBLE,

    /** Al llegar al límite la app se cierra hasta mañana: sin ampliaciones. */
    FIRM,

    /** La app está siempre en pausa; no hay tiempo diario. */
    CLOSED,
}

val AppLimit.friction: FrictionLevel
    get() = when {
        isStrictlyBlocked -> FrictionLevel.CLOSED
        extensionsPerDay > 0 -> FrictionLevel.FLEXIBLE
        else -> FrictionLevel.FIRM
    }

/** Crea el límite que corresponde a [minutes] y [friction]. Con `CLOSED` los minutos se guardan pero no se usan. */
fun appLimitOf(packageName: String, minutes: Int, friction: FrictionLevel, isLeisure: Boolean = false) = AppLimit(
    packageName = packageName,
    dailyMinutesAllowed = minutes,
    isStrictlyBlocked = friction == FrictionLevel.CLOSED,
    extensionsPerDay = if (friction == FrictionLevel.FLEXIBLE) AppLimit.DEFAULT_EXTENSIONS_PER_DAY else 0,
    isLeisure = isLeisure,
)
