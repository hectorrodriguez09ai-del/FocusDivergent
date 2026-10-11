package com.focusdivergent.app.domain.model

/**
 * Lo único del progreso que no se puede calcular a partir del resto: la mejor racha que ha habido. Se guarda
 * porque nunca debe bajar, ni siquiera si se borra la Rutina con la que se consiguió (RF-6.1).
 */
data class UserStats(
    val bestStreak: Int = 0,
)
