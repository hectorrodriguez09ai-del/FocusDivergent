package com.focusdivergent.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FtsQueryTest {

    @Test
    fun `una palabra se entrecomilla y se marca como prefijo`() {
        assertEquals("\"penta*\"", "penta".toFtsMatchQuery())
    }

    @Test
    fun `varias palabras se entrecomillan y solo la ultima es prefijo`() {
        assertEquals("\"escala\" \"menor*\"", "escala menor".toFtsMatchQuery())
    }

    @Test
    fun `los espacios sobrantes se ignoran`() {
        assertEquals("\"escala\" \"menor*\"", "   escala \t  menor  ".toFtsMatchQuery())
    }

    @Test
    fun `las comillas y asteriscos del usuario se descartan`() {
        assertEquals("\"foobar*\"", "foo\"bar*".toFtsMatchQuery())
        assertEquals("\"penta*\"", "penta**".toFtsMatchQuery())
    }

    @Test
    fun `los operadores de FTS se tratan como palabras literales`() {
        assertEquals("\"AND\" \"NEAR\" \"OR*\"", "AND NEAR OR".toFtsMatchQuery())
    }

    @Test
    fun `las palabras sin letras ni digitos se descartan`() {
        assertEquals("\"rock*\"", "- ( rock )".toFtsMatchQuery())
    }

    @Test
    fun `sin palabras devuelve nulo`() {
        assertNull("".toFtsMatchQuery())
        assertNull("   ".toFtsMatchQuery())
        assertNull("\"".toFtsMatchQuery())
        assertNull("* - ()".toFtsMatchQuery())
    }

    @Test
    fun `conserva letras acentuadas y la enie`() {
        assertEquals("\"música*\"", "música".toFtsMatchQuery())
        assertEquals("\"año*\"", "año".toFtsMatchQuery())
    }
}
