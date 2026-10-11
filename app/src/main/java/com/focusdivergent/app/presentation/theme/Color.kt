package com.focusdivergent.app.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Paleta de alto contraste y bajo estímulo (SRD §2, RNF-3).
//
// Tres tonos principales: negro (fondo), azul eléctrico (acción principal) y morado (acento y selección), con
// un azul cielo de apoyo y un ámbar suave para avisos. En modo claro el fondo es blanco y los mismos tonos se
// oscurecen para mantener el contraste.
//
// Reglas: texto a contraste AAA sobre el fondo; ningún rojo (`error` y sus contenedores son ámbar). El azul
// eléctrico no es el #0000FF puro, que sobre negro solo da 2,4:1, sino uno más luminoso que supera 4,5:1.
// Se definen todos los roles de Material 3 para que no se cuele ninguno de sus valores de serie.
// `ThemeContrastTest` comprueba todo esto sobre los valores reales.

internal val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF448AFF),
    onPrimary = Color(0xFF001133),
    primaryContainer = Color(0xFF0A2A66),
    onPrimaryContainer = Color(0xFFD6E4FF),
    inversePrimary = Color(0xFF0F52E6),
    secondary = Color(0xFFA974FF),
    onSecondary = Color(0xFF1A0540),
    secondaryContainer = Color(0xFF2E1A5E),
    onSecondaryContainer = Color(0xFFE6DBFF),
    tertiary = Color(0xFF7DD3FC),
    onTertiary = Color(0xFF00222E),
    tertiaryContainer = Color(0xFF0B3A4D),
    onTertiaryContainer = Color(0xFFD3F0FF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF17171F),
    onSurfaceVariant = Color(0xFFD0D0DC),
    surfaceTint = Color(0xFF448AFF),
    inverseSurface = Color(0xFFEEF0F4),
    inverseOnSurface = Color(0xFF000000),
    error = Color(0xFFFCD34D),
    onError = Color(0xFF2B1F00),
    errorContainer = Color(0xFF4A3800),
    onErrorContainer = Color(0xFFFFE9A8),
    outline = Color(0xFF8A8A99),
    outlineVariant = Color(0xFF3A3A47),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF262631),
    surfaceDim = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0A0A10),
    surfaceContainer = Color(0xFF111118),
    surfaceContainerHigh = Color(0xFF17171F),
    surfaceContainerHighest = Color(0xFF1E1E28),
)

internal val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF0F52E6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF001A4D),
    inversePrimary = Color(0xFF448AFF),
    secondary = Color(0xFF6D28D9),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEADDFF),
    onSecondaryContainer = Color(0xFF21005D),
    tertiary = Color(0xFF005F80),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCDEBFA),
    onTertiaryContainer = Color(0xFF001E2B),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFEEF0F4),
    onSurfaceVariant = Color(0xFF2A2A33),
    surfaceTint = Color(0xFF0F52E6),
    inverseSurface = Color(0xFF17171F),
    inverseOnSurface = Color(0xFFFFFFFF),
    error = Color(0xFF8A5A00),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFEFC2),
    onErrorContainer = Color(0xFF2B1F00),
    outline = Color(0xFF5F6370),
    outlineVariant = Color(0xFFC5C8D0),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE3E6EA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F7F9),
    surfaceContainer = Color(0xFFF0F2F5),
    surfaceContainerHigh = Color(0xFFEAECEF),
    surfaceContainerHighest = Color(0xFFE3E6EA),
)

/**
 * Colores propios de la app que Material 3 no tiene.
 *
 * Los tres de energía (azul cielo, azul eléctrico y morado, de menos a más intensidad) acompañan a las barras y
 * a la palabra «Baja / Media / Alta»: nunca son el único canal de información, y ninguno es rojo aunque el SRD
 * marque así la energía alta (RNF-3).
 */
@Immutable
data class FocusColors(
    val energyLow: Color,
    val energyMedium: Color,
    val energyHigh: Color,
    /** Tramo vacío de una barra de energía o de progreso. */
    val track: Color,
    /** Marca de «hecho»: tranquila, sin celebración. */
    val done: Color,
)

internal val DarkFocusColors = FocusColors(
    energyLow = DarkColors.tertiary,
    energyMedium = DarkColors.primary,
    energyHigh = DarkColors.secondary,
    track = DarkColors.outlineVariant,
    done = DarkColors.tertiary,
)

internal val LightFocusColors = FocusColors(
    energyLow = LightColors.tertiary,
    energyMedium = LightColors.primary,
    energyHigh = LightColors.secondary,
    track = LightColors.outlineVariant,
    done = LightColors.tertiary,
)

val LocalFocusColors = staticCompositionLocalOf { DarkFocusColors }
