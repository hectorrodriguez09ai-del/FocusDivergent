package com.focusdivergent.app.presentation.friction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerInputTest {

    private fun type(vararg digits: Int) = digits.fold(AnswerInput()) { input, digit -> input.append(digit) }

    @Test
    fun `empieza vacia y sin valor`() {
        assertTrue(AnswerInput().isEmpty)
        assertNull(AnswerInput().value)
    }

    @Test
    fun `los digitos se van anadiendo y forman el numero`() {
        assertEquals(1660L, type(1, 6, 6, 0).value)
    }

    @Test
    fun `borrar quita el ultimo digito y no falla si esta vacia`() {
        assertEquals(16L, type(1, 6, 6).backspace().value)
        assertTrue(AnswerInput().backspace().isEmpty)
    }

    @Test
    fun `un cero inicial se sustituye por el digito siguiente`() {
        assertEquals(5L, type(0, 5).value)
        assertEquals("5", type(0, 5).digits)
    }

    @Test
    fun `no admite mas digitos que el maximo`() {
        val full = type(1, 2, 3, 4, 5, 6)

        assertEquals(AnswerInput.MAX_DIGITS, full.digits.length)
        assertEquals(full, full.append(7))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `un digito fuera de rango es un error`() {
        AnswerInput().append(10)
    }
}
