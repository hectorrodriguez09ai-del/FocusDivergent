package com.focusdivergent.app.presentation.theme

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

// Movimiento: poco y con sentido. Solo fundidos y desplazamientos cortos; nada de rebotes, confeti ni
// animaciones de «tragaperras», que son justo el estímulo que esta app intenta no dar.

object Motion {
    /** Respuesta a un toque: cambio de estado, selección. */
    const val SHORT_MS = 120

    /** Entrada de una pantalla o de un bloque de contenido. */
    const val MEDIUM_MS = 220
}

/** `true` si el usuario apagó las animaciones del sistema (Ajustes > Accesibilidad o Opciones de desarrollador). */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Especificación de animación que respeta la preferencia de movimiento reducido: si está activa, no anima. */
@Composable
@ReadOnlyComposable
fun <T> focusTween(durationMillis: Int = Motion.MEDIUM_MS): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else tween(durationMillis)
