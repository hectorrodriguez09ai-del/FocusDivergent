package com.focusdivergent.app.domain.settings

import com.focusdivergent.app.domain.model.EnergyLevel
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * La energía con la que el usuario dice estar hoy. Se recuerda todo el día y se olvida al siguiente: la energía de
 * ayer no dice nada de la de hoy, así que pasada la medianoche vuelve a no haber ninguna elegida.
 */
interface DailyEnergyStore {

    /** La energía elegida para [day], o nulo si no ha elegido ninguna ese día. */
    fun observe(day: LocalDate): Flow<EnergyLevel?>

    fun get(day: LocalDate): EnergyLevel?

    fun set(day: LocalDate, level: EnergyLevel)
}
