package com.focusdivergent.app.presentation.friction

/**
 * Respuesta que el usuario va tecleando en el teclado numérico propio.
 *
 * Es el único camino para escribir la respuesta: no hay campo de texto, así que no existe ni teclado del
 * sistema ni menú de pegar, y el portapapeles queda fuera de juego (RF-1.3).
 */
data class AnswerInput(val digits: String = "") {

    val isEmpty: Boolean get() = digits.isEmpty()

    /** La respuesta como número, o nulo si todavía no se ha escrito nada. */
    val value: Long? get() = digits.toLongOrNull()

    fun append(digit: Int): AnswerInput {
        require(digit in 0..9) { "Un dígito va de 0 a 9: $digit" }
        return when {
            digits.length >= MAX_DIGITS -> this
            digits == "0" -> copy(digits = digit.toString()) // sin ceros a la izquierda
            else -> copy(digits = digits + digit)
        }
    }

    fun backspace(): AnswerInput = copy(digits = digits.dropLast(1))

    companion object {
        /** Las respuestas del generador no pasan de 4 cifras; el margen evita desbordes. */
        const val MAX_DIGITS = 6
    }
}
