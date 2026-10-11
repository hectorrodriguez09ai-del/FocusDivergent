package com.focusdivergent.app.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La paleta es un requisito, no una preferencia: alto contraste (SRD §2) y ningún rojo (RNF-3). Estos tests
 * calculan el contraste WCAG sobre los colores reales, para que un retoque futuro no lo rompa sin avisar.
 */
class ThemeContrastTest {

    private data class Palette(val name: String, val scheme: ColorScheme, val focus: FocusColors)

    private val palettes = listOf(
        Palette("oscuro", DarkColors, DarkFocusColors),
        Palette("claro", LightColors, LightFocusColors),
    )

    // Luminancia relativa y razón de contraste según WCAG 2.x.
    private fun channel(c: Float): Double = if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    private fun luminance(c: Color) = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun assertContrast(palette: String, what: String, fg: Color, bg: Color, atLeast: Double) {
        val ratio = contrast(fg, bg)
        assertTrue("[$palette] $what: contraste %.2f, se exige %.1f".format(ratio, atLeast), ratio >= atLeast)
    }

    @Test
    fun `el texto principal tiene contraste AAA sobre el fondo y sobre las tarjetas`() {
        for ((name, s) in palettes) {
            assertContrast(name, "onBackground/background", s.onBackground, s.background, 7.0)
            assertContrast(name, "onSurface/surface", s.onSurface, s.surface, 7.0)
            assertContrast(name, "onSurface/surfaceContainer", s.onSurface, s.surfaceContainer, 7.0)
            assertContrast(name, "onSurface/surfaceContainerHighest", s.onSurface, s.surfaceContainerHighest, 7.0)
        }
    }

    @Test
    fun `el texto secundario tambien llega a AAA`() {
        for ((name, s) in palettes) {
            assertContrast(name, "onSurfaceVariant/surface", s.onSurfaceVariant, s.surface, 7.0)
            assertContrast(name, "onSurfaceVariant/surfaceContainer", s.onSurfaceVariant, s.surfaceContainer, 7.0)
        }
    }

    @Test
    fun `cada color de relleno contrasta con el texto que lleva encima`() {
        for ((name, s) in palettes) {
            assertContrast(name, "onPrimary/primary", s.onPrimary, s.primary, 4.5)
            assertContrast(name, "onPrimaryContainer/primaryContainer", s.onPrimaryContainer, s.primaryContainer, 4.5)
            assertContrast(name, "onSecondary/secondary", s.onSecondary, s.secondary, 4.5)
            assertContrast(name, "onSecondaryContainer/secondaryContainer", s.onSecondaryContainer, s.secondaryContainer, 4.5)
            assertContrast(name, "onTertiary/tertiary", s.onTertiary, s.tertiary, 4.5)
            assertContrast(name, "onTertiaryContainer/tertiaryContainer", s.onTertiaryContainer, s.tertiaryContainer, 4.5)
            assertContrast(name, "onError/error", s.onError, s.error, 4.5)
            assertContrast(name, "onErrorContainer/errorContainer", s.onErrorContainer, s.errorContainer, 4.5)
            assertContrast(name, "inverseOnSurface/inverseSurface", s.inverseOnSurface, s.inverseSurface, 4.5)
        }
    }

    @Test
    fun `los colores de acento sirven como texto sobre el fondo`() {
        for ((name, s) in palettes) {
            assertContrast(name, "primary/surface", s.primary, s.surface, 4.5)
            assertContrast(name, "secondary/surface", s.secondary, s.surface, 4.5)
            assertContrast(name, "error (aviso ambar)/surface", s.error, s.surface, 4.5)
            assertContrast(name, "primary/surfaceContainer", s.primary, s.surfaceContainer, 4.5)
        }
    }

    @Test
    fun `las barras de energia y los bordes de los controles se distinguen del fondo`() {
        // Elementos gráficos y límites de controles: WCAG pide 3:1.
        for ((name, s, f) in palettes) {
            for (bg in listOf(s.surface, s.surfaceContainer, s.surfaceContainerHighest)) {
                assertContrast(name, "energyLow", f.energyLow, bg, 3.0)
                assertContrast(name, "energyMedium", f.energyMedium, bg, 3.0)
                assertContrast(name, "energyHigh", f.energyHigh, bg, 3.0)
            }
            assertContrast(name, "outline/surface", s.outline, s.surface, 3.0)
        }
    }

    @Test
    fun `no hay ningun rojo en toda la paleta`() {
        for ((name, s, f) in palettes) {
            val all = mapOf(
                "primary" to s.primary, "onPrimary" to s.onPrimary,
                "primaryContainer" to s.primaryContainer, "onPrimaryContainer" to s.onPrimaryContainer,
                "inversePrimary" to s.inversePrimary,
                "secondary" to s.secondary, "onSecondary" to s.onSecondary,
                "secondaryContainer" to s.secondaryContainer, "onSecondaryContainer" to s.onSecondaryContainer,
                "tertiary" to s.tertiary, "onTertiary" to s.onTertiary,
                "tertiaryContainer" to s.tertiaryContainer, "onTertiaryContainer" to s.onTertiaryContainer,
                "background" to s.background, "onBackground" to s.onBackground,
                "surface" to s.surface, "onSurface" to s.onSurface,
                "surfaceVariant" to s.surfaceVariant, "onSurfaceVariant" to s.onSurfaceVariant,
                "surfaceTint" to s.surfaceTint,
                "inverseSurface" to s.inverseSurface, "inverseOnSurface" to s.inverseOnSurface,
                "error" to s.error, "onError" to s.onError,
                "errorContainer" to s.errorContainer, "onErrorContainer" to s.onErrorContainer,
                "outline" to s.outline, "outlineVariant" to s.outlineVariant, "scrim" to s.scrim,
                "surfaceBright" to s.surfaceBright, "surfaceDim" to s.surfaceDim,
                "surfaceContainerLowest" to s.surfaceContainerLowest, "surfaceContainerLow" to s.surfaceContainerLow,
                "surfaceContainer" to s.surfaceContainer, "surfaceContainerHigh" to s.surfaceContainerHigh,
                "surfaceContainerHighest" to s.surfaceContainerHighest,
                "energyLow" to f.energyLow, "energyMedium" to f.energyMedium, "energyHigh" to f.energyHigh,
                "track" to f.track, "done" to f.done,
            )
            for ((role, color) in all) {
                assertTrue("[$name] $role es rojizo: ${describe(color)}", !isReddish(color))
            }
        }
    }

    @Test
    fun `el detector de rojos reconoce los rojos de serie de Material`() {
        // Sin esta comprobación el test anterior pasaría aunque el detector no detectara nada.
        assertTrue(isReddish(Color(0xFFB3261E))) // error claro de Material 3
        assertTrue(isReddish(Color(0xFFF2B8B5))) // error oscuro de Material 3
        assertTrue(isReddish(Color(0xFF93000A))) // errorContainer oscuro de Material 3
        assertTrue(isReddish(Color(0xFFDC2626))) // «destructive» de la paleta que propuso la skill
        assertTrue(!isReddish(Color(0xFFFCD34D))) // nuestro ámbar
        assertTrue(!isReddish(Color(0xFF8A5A00))) // nuestro ámbar en modo claro
    }

    @Test
    fun `los tonos principales son negro, azul electrico y morado`() {
        assertTrue("el fondo oscuro es negro puro", DarkColors.background == Color(0xFF000000))
        assertTrue("la superficie oscura es negro puro", DarkColors.surface == Color(0xFF000000))
        for ((name, s) in palettes) {
            val blue = hue(s.primary)!!
            val purple = hue(s.secondary)!!
            assertTrue("[$name] primary debe ser azul (tono %.0f°)".format(blue), blue in 205f..235f)
            assertTrue("[$name] secondary debe ser morado (tono %.0f°)".format(purple), purple in 255f..285f)
            // «Eléctrico»: un azul saturado, no un azul grisáceo.
            assertTrue("[$name] el azul debe ser saturado", saturation(s.primary) >= 0.7f)
        }
    }

    // Rojizo = tono entre 340° y 20° con color apreciable (los grises y casi negros no cuentan).
    private fun isReddish(c: Color): Boolean {
        val h = hue(c) ?: return false
        return h < 20f || h > 340f
    }

    private fun saturation(c: Color): Float {
        val mx = max(c.red, max(c.green, c.blue))
        val mn = min(c.red, min(c.green, c.blue))
        return if (mx == 0f) 0f else (mx - mn) / mx
    }

    /** Tono HSV en grados, o nulo si el color es un gris o casi negro sin tono apreciable. */
    private fun hue(c: Color): Float? {
        val mx = max(c.red, max(c.green, c.blue))
        val mn = min(c.red, min(c.green, c.blue))
        val delta = mx - mn
        if (mx == 0f || delta / mx < 0.25f || delta < 0.08f) return null
        return when (mx) {
            c.red -> 60f * (((c.green - c.blue) / delta).mod(6f))
            c.green -> 60f * ((c.blue - c.red) / delta + 2f)
            else -> 60f * ((c.red - c.green) / delta + 4f)
        }
    }

    private fun describe(c: Color) =
        "#%02X%02X%02X".format((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
}
