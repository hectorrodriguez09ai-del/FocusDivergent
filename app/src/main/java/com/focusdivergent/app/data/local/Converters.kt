package com.focusdivergent.app.data.local

import androidx.room.TypeConverter
import com.focusdivergent.app.domain.model.EnergyLevel

/**
 * Conversores de tipos para Room.
 *
 * Solo hace falta el de [EnergyLevel], que se guarda como entero (1, 2, 3). El resto de enums los
 * persiste Room por su nombre sin ayuda.
 */
class Converters {

    @TypeConverter
    fun energyLevelToInt(level: EnergyLevel): Int = level.value

    @TypeConverter
    fun intToEnergyLevel(value: Int): EnergyLevel = EnergyLevel.fromValue(value)
}
