package com.focusdivergent.app.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Escala de espaciado. Usar siempre estos pasos, nunca valores sueltos, para que el ritmo sea constante. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp

    /** Margen lateral de todas las pantallas. */
    val screen = 20.dp
}

/** Tamaños que no son espaciado. */
object Sizes {
    /** Mínimo de Android para cualquier cosa que se pueda tocar. */
    val touchTarget = 48.dp

    /** Alto de los botones de acción principal: grandes, fáciles de acertar. */
    val primaryAction = 56.dp

    val icon = 24.dp
}

internal val FocusShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
