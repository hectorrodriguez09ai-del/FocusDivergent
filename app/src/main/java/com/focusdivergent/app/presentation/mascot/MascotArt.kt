package com.focusdivergent.app.presentation.mascot

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.focusdivergent.app.R
import com.focusdivergent.app.domain.reward.Cosmetic
import com.focusdivergent.app.domain.reward.Mascot
import com.focusdivergent.app.domain.reward.MascotStages
import com.focusdivergent.app.presentation.theme.LocalReducedMotion
import kotlin.math.roundToInt

// La mascota se dibuja sobre una cuadrícula de «píxeles de arte»: el escenario mide 56 × 40 y cada sprite 40 × 40
// (el lienzo de las imágenes de res/drawable-nodpi). Todo se escala con el mismo píxel, sin suavizado, para que el
// arte se vea nítido a cualquier tamaño.

private const val STAGE_W = 56
private const val STAGE_H = 40
private const val SPRITE = 40
private const val GROUND_TOP = 33

/** Proporción del escenario, para que las pantallas reserven el hueco. */
const val STAGE_ASPECT_RATIO = STAGE_W.toFloat() / STAGE_H.toFloat()

/** La imagen de [mascot] en la etapa [stage] (de 1 a [MascotStages.COUNT]). */
@DrawableRes
fun spriteRes(mascot: Mascot, stage: Int): Int {
    val index = stage.coerceIn(1, MascotStages.COUNT) - 1
    return when (mascot) {
        Mascot.FOX -> FOX[index]
        Mascot.CAT -> CAT[index]
        Mascot.ALIEN -> ALIEN[index]
    }
}

private val FOX = intArrayOf(R.drawable.mascot_fox_1, R.drawable.mascot_fox_2, R.drawable.mascot_fox_3, R.drawable.mascot_fox_4, R.drawable.mascot_fox_5)
private val CAT = intArrayOf(R.drawable.mascot_cat_1, R.drawable.mascot_cat_2, R.drawable.mascot_cat_3, R.drawable.mascot_cat_4, R.drawable.mascot_cat_5)
private val ALIEN = intArrayOf(R.drawable.mascot_alien_1, R.drawable.mascot_alien_2, R.drawable.mascot_alien_3, R.drawable.mascot_alien_4, R.drawable.mascot_alien_5)

/**
 * Una mascota en su escenario, con el objeto que lleve al lado. Se ve igual en claro y en oscuro: es una pieza de
 * arte, no un componente de la interfaz.
 *
 * Respira muy despacio (sube un píxel y baja) salvo que el usuario tenga las animaciones apagadas o [animated] sea
 * falso. Nada de rebotes ni destellos (RNF-3).
 *
 * @param contentDescription lo que lee un lector de pantalla; nulo si el texto de al lado ya lo dice.
 */
@Composable
fun MascotStage(
    mascot: Mascot,
    stage: Int,
    scenery: Cosmetic,
    prop: Cosmetic?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    animated: Boolean = true,
) {
    val sprite = ImageBitmap.imageResource(spriteRes(mascot, stage))
    val breath = rememberBreath(animated)
    Canvas(
        modifier = modifier
            .aspectRatio(STAGE_ASPECT_RATIO)
            .clip(RoundedCornerShape(16.dp))
            .describedAs(contentDescription),
    ) {
        val unit = size.height / STAGE_H
        drawScenery(scenery, unit)
        val shift = if (prop != null) 4 else 8
        val lift = if (breath.value >= 0.5f) 1 else 0
        drawSprite(sprite, x = shift, y = -lift, unit = unit)
        prop?.let { drawProp(it, unit) }
    }
}

/** Solo la mascota, sin escenario. Con [silhouette] se ve una sombra: lo que aún no se ha alcanzado. */
@Composable
fun MascotSprite(
    mascot: Mascot,
    stage: Int,
    modifier: Modifier = Modifier,
    silhouette: Boolean = false,
    contentDescription: String? = null,
) {
    val sprite = ImageBitmap.imageResource(spriteRes(mascot, stage))
    Canvas(modifier = modifier.aspectRatio(1f).describedAs(contentDescription)) {
        val unit = size.height / SPRITE
        drawSprite(sprite, x = 0, y = 0, unit = unit, colorFilter = if (silhouette) SILHOUETTE else null)
    }
}

private val SILHOUETTE = ColorFilter.tint(Color(0xFF2A3350))

private fun Modifier.describedAs(description: String?): Modifier =
    if (description == null) {
        clearAndSetSemantics { }
    } else {
        semantics {
            contentDescription = description
            role = Role.Image
        }
    }

@Composable
private fun rememberBreath(animated: Boolean): State<Float> {
    if (!animated || LocalReducedMotion.current) return remember { mutableFloatStateOf(0f) }
    return rememberInfiniteTransition(label = "breath").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1_600, easing = LinearEasing), RepeatMode.Restart),
        label = "breath",
    )
}

// ---- Dibujo

private fun DrawScope.edge(units: Int, unit: Float): Float = (units * unit).roundToInt().toFloat()

/** Un rectángulo en píxeles de arte; los bordes se redondean para que dos vecinos nunca dejen una línea entre ellos. */
private fun DrawScope.block(x: Int, y: Int, w: Int, h: Int, color: Color, unit: Float) {
    val left = edge(x, unit)
    val top = edge(y, unit)
    drawRect(color, Offset(left, top), Size(edge(x + w, unit) - left, edge(y + h, unit) - top))
}

private fun DrawScope.drawSprite(sprite: ImageBitmap, x: Int, y: Int, unit: Float, colorFilter: ColorFilter? = null) {
    drawImage(
        image = sprite,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(sprite.width, sprite.height),
        dstOffset = IntOffset(edge(x, unit).toInt(), edge(y, unit).toInt()),
        dstSize = IntSize(edge(SPRITE, unit).toInt(), edge(SPRITE, unit).toInt()),
        filterQuality = FilterQuality.None,
        colorFilter = colorFilter,
    )
}

private fun DrawScope.drawProp(prop: Cosmetic, unit: Float) {
    val art = when (prop) {
        Cosmetic.PROP_PLANT -> PropArtPlant
        Cosmetic.PROP_BOOKS -> PropArtBooks
        Cosmetic.PROP_LAMP -> PropArtLamp
        Cosmetic.PROP_STAR -> PropArtStar
        else -> return
    }
    val x = 54 - art.width
    // La estrella flota; el resto apoya en el suelo, donde apoyan los pies de la mascota.
    val y = if (prop == Cosmetic.PROP_STAR) 20 else 38 - art.height
    if (prop == Cosmetic.PROP_LAMP) {
        drawCircle(Color(0x33FCD34D), radius = 11 * unit, center = Offset(edge(x + art.width / 2, unit) + unit / 2, edge(y + 1, unit)))
    }
    art.rows.forEachIndexed { row, line ->
        line.forEachIndexed { col, ch ->
            art.palette[ch]?.let { color -> block(x + col, y + row, 1, 1, color, unit) }
        }
    }
}

private val StarColors = listOf(Color(0xFFF8FAFC), Color(0xFF93C5FD))

private val Stars = listOf(
    3 to 4, 9 to 11, 14 to 3, 20 to 8, 27 to 2, 33 to 12, 38 to 5, 44 to 3, 50 to 10, 53 to 5, 6 to 19, 24 to 15, 48 to 17,
)

private fun DrawScope.drawStars(unit: Float) {
    Stars.forEachIndexed { i, (x, y) -> block(x, y, 1, 1, StarColors[i % StarColors.size], unit) }
}

private fun DrawScope.drawGround(top: Color, edgeColor: Color, unit: Float) {
    block(0, GROUND_TOP, STAGE_W, STAGE_H - GROUND_TOP, top, unit)
    block(0, GROUND_TOP, STAGE_W, 1, edgeColor, unit)
}

private fun DrawScope.drawScenery(scenery: Cosmetic, unit: Float) {
    when (scenery) {
        Cosmetic.SCENERY_NIGHT -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0B1030), Color(0xFF1C2A66))))
            drawStars(unit)
            // Luna: un disco con una mordida de sombra, en píxeles.
            drawCircle(Color(0xFFE8ECFF), radius = 4.5f * unit, center = Offset(46 * unit, 9 * unit))
            drawCircle(Color(0xFF1C2A66), radius = 3.6f * unit, center = Offset(47.6f * unit, 8 * unit))
            drawGround(Color(0xFF0A0E24), Color(0xFF1E2A5E), unit)
        }
        Cosmetic.SCENERY_FOREST -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0F3327), Color(0xFF1A5440))))
            listOf(6 to 13, 20 to 10, 36 to 12, 50 to 9).forEach { (x, h) -> drawPine(x, h, Color(0xFF175742), unit) }
            listOf(13 to 12, 29 to 14, 44 to 11).forEach { (x, h) -> drawPine(x, h, Color(0xFF1F6B4F), unit) }
            drawGround(Color(0xFF0B2A20), Color(0xFF2F8A66), unit)
        }
        Cosmetic.SCENERY_DUSK -> {
            val bands = listOf(Color(0xFF2E1A5E), Color(0xFF47298A), Color(0xFF6B3FA0), Color(0xFFB57A32), Color(0xFFD9A441))
            val rows = listOf(0, 8, 16, 24, 29)
            bands.forEachIndexed { i, c -> block(0, rows[i], STAGE_W, (rows.getOrNull(i + 1) ?: GROUND_TOP) - rows[i], c, unit) }
            drawCircle(Color(0xFFF6C453), radius = 7 * unit, center = Offset(28 * unit, GROUND_TOP * unit))
            drawGround(Color(0xFF2A1840), Color(0xFF4A2C86), unit)
        }
        Cosmetic.SCENERY_NEBULA -> {
            drawRect(Color(0xFF070A1F))
            drawCircle(
                Brush.radialGradient(listOf(Color(0x806D28D9), Color(0x006D28D9)), Offset(16 * unit, 12 * unit), 20 * unit),
                radius = 20 * unit,
                center = Offset(16 * unit, 12 * unit),
            )
            drawCircle(
                Brush.radialGradient(listOf(Color(0x802563EB), Color(0x002563EB)), Offset(42 * unit, 20 * unit), 22 * unit),
                radius = 22 * unit,
                center = Offset(42 * unit, 20 * unit),
            )
            drawStars(unit)
            drawGround(Color(0xFF0B0F2E), Color(0xFF3B2A7A), unit)
        }
        else -> {
            // «Tranquilo», el fondo de serie: un azul pizarra sin nada que mirar.
            drawRect(Color(0xFF26314A))
            drawGround(Color(0xFF1B2438), Color(0xFF34425F), unit)
        }
    }
}

/** Un pino de [height] píxeles de alto: capas que se ensanchan hacia abajo y un tronco. */
private fun DrawScope.drawPine(centerX: Int, height: Int, color: Color, unit: Float) {
    val base = GROUND_TOP
    for (row in 0 until height) {
        val half = row / 3
        block(centerX - half, base - height + row, half * 2 + 1, 1, color, unit)
    }
    block(centerX, base, 1, 1, color, unit)
}
