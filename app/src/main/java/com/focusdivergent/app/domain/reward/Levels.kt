package com.focusdivergent.app.domain.reward

/**
 * El nivel en el que está el usuario y cuánto lleva del siguiente.
 *
 * @property xpIntoLevel el XP acumulado dentro del nivel actual.
 * @property xpForLevel el XP que dura el nivel actual: cuando [xpIntoLevel] lo alcanza, se sube.
 */
data class LevelProgress(
    val level: Int,
    val totalXp: Int,
    val xpIntoLevel: Int,
    val xpForLevel: Int,
) {
    /** Cuánto de la barra está llena, de 0 a 1. */
    val fraction: Float get() = (xpIntoLevel.toFloat() / xpForLevel.toFloat()).coerceIn(0f, 1f)
}

/**
 * La curva de niveles: suave al principio, para que el primer día ya se note, y cada nivel pide un poco más que el
 * anterior (`30 + 10 × nivel` de XP). No tiene techo.
 */
object Levels {

    /** XP total necesario para llegar al nivel [level] (el 1 se alcanza con 0). */
    fun xpToReach(level: Int): Int = (level - 1) * (5 * level + 30)

    /** Lo que dura el nivel [level]: el XP que hay que sumar para pasar al siguiente. */
    fun xpForLevel(level: Int): Int = 10 * level + 30

    fun of(totalXp: Int): LevelProgress {
        val xp = totalXp.coerceAtLeast(0)
        var level = 1
        while (xpToReach(level + 1) <= xp) level++
        return LevelProgress(
            level = level,
            totalXp = xp,
            xpIntoLevel = xp - xpToReach(level),
            xpForLevel = xpForLevel(level),
        )
    }
}
