package com.focusdivergent.app.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Tipografía: la fuente del sistema, a propósito. Respeta la letra y el tamaño que el usuario eligió en los
// ajustes del móvil y no necesita descargar nada (RNF-1). Sobre la escala de Material 3 solo se cambia lo que
// ayuda a leer: cuerpo con interlineado de 1,5 y títulos con más peso para que la jerarquía se vea de un vistazo.

private val Base = Typography()

internal val FocusTypography = Base.copy(
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = Base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = Base.bodySmall.copy(fontSize = 13.sp, lineHeight = 20.sp),
    labelLarge = Base.labelLarge.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
)

/** Cifras de ancho fijo para contadores y cuentas atrás: los dígitos no «bailan» al cambiar. */
fun TextStyle.tabularNumbers(): TextStyle = copy(fontFeatureSettings = "tnum")
