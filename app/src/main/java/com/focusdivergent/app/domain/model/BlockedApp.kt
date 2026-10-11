package com.focusdivergent.app.domain.model

/** Por qué una app debe bloquearse. */
sealed interface BlockReason {

    /** La app está bloqueada siempre, sin depender del tiempo consumido. */
    data object StrictlyBlocked : BlockReason

    /** Se agotó el tiempo diario permitido, ya contando las ampliaciones ganadas hoy. */
    data class LimitReached(val usedMillis: Long, val allowedMillis: Long) : BlockReason
}

/**
 * Una app en primer plano que hay que cubrir con la pantalla de pausa.
 *
 * @property extensionsLeft ampliaciones de tiempo que aún puede ganar hoy; con 0 no se ofrece el reto matemático.
 * @property extensionsPerDay las que permite su límite en total: 0 si es de nivel firme o cerrado. Sirve para
 * no decir «ya usaste tus ampliaciones» a quien nunca las tuvo.
 */
data class BlockedApp(
    val packageName: String,
    val reason: BlockReason,
    val extensionsLeft: Int,
    val extensionsPerDay: Int = AppLimit.DEFAULT_EXTENSIONS_PER_DAY,
) : Interruption {

    override val key: String get() = "blocked:$packageName"
}
