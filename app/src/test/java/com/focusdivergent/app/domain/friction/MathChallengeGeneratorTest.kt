package com.focusdivergent.app.domain.friction

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MathChallengeGeneratorTest {

    private val generator = MathChallengeGenerator()

    @Test
    fun `la respuesta coincide con evaluar el enunciado, en miles de operaciones`() {
        val random = Random(42)

        repeat(5_000) {
            val challenge = generator.generate(random)
            assertEquals(challenge.expression, evaluate(challenge.expression), challenge.answer)
        }
    }

    @Test
    fun `la respuesta es siempre positiva y cabe en cuatro cifras`() {
        val random = Random(7)

        repeat(5_000) {
            val challenge = generator.generate(random)
            assertTrue(challenge.expression, challenge.answer in 1..9_999)
        }
    }

    @Test
    fun `salen las tres formas de operacion`() {
        val random = Random(1)
        val shapes = List(300) { generator.generate(random).expression }
            .map { it.replace(Regex("\\d+"), "n") }
            .toSet()

        assertEquals(setOf("n × n − n", "n × n + n × n", "(n + n) × n − n"), shapes)
    }

    @Test
    fun `con la misma semilla sale la misma operacion`() {
        assertEquals(generator.generate(Random(99)), generator.generate(Random(99)))
    }

    @Test
    fun `con semillas distintas salen operaciones distintas`() {
        val distinct = (1..50).map { generator.generate(Random(it)).expression }.toSet()

        assertTrue("Solo ${distinct.size} operaciones distintas de 50", distinct.size > 40)
        assertNotEquals(1, distinct.size)
    }

    // --- Evaluador independiente del generador: números, + − ×, paréntesis. ---

    private fun evaluate(expression: String): Long = Parser(expression.replace(" ", "")).parse()

    private class Parser(private val text: String) {
        private var pos = 0

        fun parse(): Long = expression().also { check(pos == text.length) { "Sobra texto en '$text'" } }

        private fun expression(): Long {
            var value = term()
            while (pos < text.length && (text[pos] == '+' || text[pos] == '−')) {
                val op = text[pos++]
                val right = term()
                value = if (op == '+') value + right else value - right
            }
            return value
        }

        private fun term(): Long {
            var value = factor()
            while (pos < text.length && text[pos] == '×') {
                pos++
                value *= factor()
            }
            return value
        }

        private fun factor(): Long {
            if (text[pos] == '(') {
                pos++
                return expression().also { check(text[pos++] == ')') }
            }
            val start = pos
            while (pos < text.length && text[pos].isDigit()) pos++
            return text.substring(start, pos).toLong()
        }
    }
}
