package com.focusdivergent.app.domain.model

/**
 * Algo que el sistema pone delante del usuario para romper la inercia: el bloqueo de una app y el aviso
 * preventivo de una Obligación; el Reality Check se añade en esta misma fase.
 */
sealed interface Interruption {

    /**
     * Identifica la interrupción. Mientras la clave no cambie, el overlay que ya está en pantalla
     * sigue valiendo y no se redibuja.
     */
    val key: String
}
