package com.focusdivergent.app.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Tema de la app: paleta de alto contraste sin rojos ([Color.kt]), fuente del sistema ([Type.kt]), formas y
 * espaciado propios, y la preferencia de movimiento reducido. Sigue el modo claro u oscuro del sistema.
 */
@Composable
fun FocusDivergentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalFocusColors provides if (darkTheme) DarkFocusColors else LightFocusColors,
        LocalReducedMotion provides rememberReducedMotion(),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = FocusTypography,
            shapes = FocusShapes,
            content = content,
        )
    }
}

/** Acceso a los colores propios de la app, al estilo de `MaterialTheme.colorScheme`. */
object FocusTheme {
    val colors: FocusColors
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusColors.current
}
