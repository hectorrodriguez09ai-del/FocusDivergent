package com.focusdivergent.app.domain.friction

import javax.inject.Inject
import kotlin.random.Random

/** Una operación que el usuario debe resolver a mano; [answer] es siempre un entero positivo. */
data class MathChallenge(val expression: String, val answer: Long)

/**
 * Genera las operaciones de la fricción matemática (RF-1.3).
 *
 * Son cálculos de cabeza lo bastante pesados como para frenar un impulso (multiplicar dos números de dos
 * cifras y ajustar el resultado), pero de respuesta única y entera. Hay tres formas, para que no se pueda
 * memorizar un patrón: `a × b − c`, `a × b + c × d` y `(a + b) × c − d`.
 */
open class MathChallengeGenerator @Inject constructor() {

    // `open` para que los tests de pantalla puedan fijar el reto y resolverlo sin parsear el enunciado.
    open fun generate(random: Random = Random.Default): MathChallenge = when (random.nextInt(SHAPES)) {
        0 -> productMinusConstant(random)
        1 -> sumOfProducts(random)
        else -> groupedSumTimesFactorMinusConstant(random)
    }

    // a × b − c. El tope de c garantiza que el resultado sea positivo.
    private fun productMinusConstant(random: Random): MathChallenge {
        val a = random.nextInt(13, 80)
        val b = random.nextInt(12, 50)
        val c = random.nextInt(10, minOf(200, a * b))
        return MathChallenge("$a × $b − $c", (a * b - c).toLong())
    }

    // a × b + c × d
    private fun sumOfProducts(random: Random): MathChallenge {
        val a = random.nextInt(12, 40)
        val b = random.nextInt(6, 20)
        val c = random.nextInt(12, 40)
        val d = random.nextInt(6, 20)
        return MathChallenge("$a × $b + $c × $d", (a * b + c * d).toLong())
    }

    // (a + b) × c − d. De nuevo, el tope de d mantiene el resultado positivo.
    private fun groupedSumTimesFactorMinusConstant(random: Random): MathChallenge {
        val a = random.nextInt(21, 100)
        val b = random.nextInt(21, 100)
        val c = random.nextInt(3, 10)
        val d = random.nextInt(10, minOf(150, (a + b) * c))
        return MathChallenge("($a + $b) × $c − $d", ((a + b) * c - d).toLong())
    }

    private companion object {
        const val SHAPES = 3
    }
}
