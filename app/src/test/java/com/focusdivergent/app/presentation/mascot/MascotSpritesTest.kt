package com.focusdivergent.app.presentation.mascot

import com.focusdivergent.app.domain.reward.Mascot
import com.focusdivergent.app.domain.reward.MascotStages
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las 15 imágenes de la hoja de sprites (3 mascotas × 5 etapas): existen, comparten lienzo de 40 × 40, tienen
 * fondo transparente y algo dibujado, y se apoyan abajo para crecer desde el mismo suelo.
 */
class MascotSpritesTest {

    private val resources = File("src/main/res/drawable-nodpi")

    private fun file(mascot: String, stage: Int) = File(resources, "mascot_${mascot}_$stage.png")

    private val names = mapOf(Mascot.FOX to "fox", Mascot.CAT to "cat", Mascot.ALIEN to "alien")

    @Test
    fun `hay una imagen por cada mascota y etapa`() {
        assertEquals(3, names.size)
        for (name in names.values) for (stage in 1..MascotStages.COUNT) {
            assertTrue("falta ${file(name, stage)}", file(name, stage).isFile)
        }
    }

    @Test
    fun `todas miden 40 por 40 y tienen contenido y transparencia`() {
        for (name in names.values) for (stage in 1..MascotStages.COUNT) {
            val image = ImageIO.read(file(name, stage))
            assertEquals("$name $stage ancho", 40, image.width)
            assertEquals("$name $stage alto", 40, image.height)

            var opaque = 0
            var transparent = 0
            for (y in 0 until image.height) for (x in 0 until image.width) {
                val alpha = image.getRGB(x, y) ushr 24
                if (alpha == 255) opaque++ else if (alpha == 0) transparent++
            }
            assertTrue("$name $stage sin dibujo", opaque > 150)
            assertTrue("$name $stage sin fondo transparente", transparent > 300)
            // Ningún píxel a medias: es arte de píxeles, sin suavizado.
            assertEquals("$name $stage tiene píxeles semitransparentes", image.width * image.height, opaque + transparent)
        }
    }

    @Test
    fun `cada sprite apoya en el suelo, en la ultima fila util del lienzo`() {
        for (name in names.values) for (stage in 1..MascotStages.COUNT) {
            val image = ImageIO.read(file(name, stage))
            val lowest = (0 until image.height).last { y -> (0 until image.width).any { x -> image.getRGB(x, y) ushr 24 == 255 } }
            assertEquals("$name $stage no apoya en el suelo", 38, lowest)
        }
    }

    @Test
    fun `cada etapa de cada mascota apunta a su propia imagen`() {
        val ids = Mascot.entries.flatMap { mascot -> (1..MascotStages.COUNT).map { spriteRes(mascot, it) } }

        assertEquals(15, ids.toSet().size)
    }

    @Test
    fun `una etapa fuera de rango se ajusta en vez de romper`() {
        assertEquals(spriteRes(Mascot.FOX, 1), spriteRes(Mascot.FOX, 0))
        assertEquals(spriteRes(Mascot.FOX, MascotStages.COUNT), spriteRes(Mascot.FOX, 99))
    }
}
