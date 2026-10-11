package com.focusdivergent.app.domain.reward

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelsTest {

    @Test
    fun `el nivel 1 se alcanza con cero XP y los siguientes piden un poco mas cada vez`() {
        assertEquals(0, Levels.xpToReach(1))
        assertEquals(40, Levels.xpToReach(2))
        assertEquals(90, Levels.xpToReach(3))
        assertEquals(300, Levels.xpToReach(6))
        assertEquals(720, Levels.xpToReach(10))
        assertEquals(1470, Levels.xpToReach(15))
    }

    @Test
    fun `lo que dura un nivel crece de diez en diez`() {
        assertEquals(40, Levels.xpForLevel(1))
        assertEquals(50, Levels.xpForLevel(2))
        assertEquals(60, Levels.xpForLevel(3))
    }

    @Test
    fun `el XP total hasta un nivel es la suma de lo que duran los anteriores`() {
        for (level in 1..60) {
            assertEquals(Levels.xpToReach(level), (1 until level).sumOf { Levels.xpForLevel(it) })
        }
    }

    @Test
    fun `se esta en el nivel cuyo tramo contiene el XP, en cada punto de la curva`() {
        for (xp in 0..6000) {
            val progress = Levels.of(xp)
            assertTrue("xp $xp", Levels.xpToReach(progress.level) <= xp)
            assertTrue("xp $xp", xp < Levels.xpToReach(progress.level + 1))
            assertEquals(xp - Levels.xpToReach(progress.level), progress.xpIntoLevel)
            assertEquals(Levels.xpForLevel(progress.level), progress.xpForLevel)
        }
    }

    @Test
    fun `justo al llegar al XP de un nivel se sube y la barra vuelve a vacia`() {
        assertEquals(LevelProgress(level = 1, totalXp = 39, xpIntoLevel = 39, xpForLevel = 40), Levels.of(39))
        assertEquals(LevelProgress(level = 2, totalXp = 40, xpIntoLevel = 0, xpForLevel = 50), Levels.of(40))
    }

    @Test
    fun `un XP negativo se trata como cero`() {
        assertEquals(Levels.of(0), Levels.of(-30))
    }

    @Test
    fun `la barra se llena de 0 a 1 dentro del nivel`() {
        assertEquals(0f, Levels.of(0).fraction, 0f)
        assertEquals(0.5f, Levels.of(20).fraction, 0.0001f)
        assertEquals(0f, Levels.of(40).fraction, 0f)
    }

    @Test
    fun `las etapas empiezan en los niveles 1, 3, 6, 10 y 15`() {
        assertEquals(listOf(1, 1, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 4, 5, 5, 5), (1..17).map { MascotStages.stageOf(it) })
        assertEquals(MascotStages.COUNT, MascotStages.LEVELS.size)
        assertEquals(5, MascotStages.stageOf(500))
        assertEquals(1, MascotStages.stageOf(0))
    }
}
