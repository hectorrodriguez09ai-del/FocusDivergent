package com.focusdivergent.app.data.settings

import android.content.Context
import androidx.core.content.edit
import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.settings.DailyEnergyStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Guarda solo la última energía elegida y el día en que se eligió (días desde 1970, como en `routine_completions`).
 * Al preguntar por otro día, no hay nada: no hace falta limpiar nada a medianoche.
 */
@Singleton
class SharedPreferencesDailyEnergyStore @Inject constructor(
    @ApplicationContext context: Context,
) : DailyEnergyStore {

    private data class Stored(val day: Long, val level: EnergyLevel)

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    override fun observe(day: LocalDate): Flow<EnergyLevel?> =
        state.map { stored -> stored?.takeIf { it.day == day.toEpochDay() }?.level }

    override fun get(day: LocalDate): EnergyLevel? = state.value?.takeIf { it.day == day.toEpochDay() }?.level

    override fun set(day: LocalDate, level: EnergyLevel) {
        prefs.edit {
            putLong(KEY_DAY, day.toEpochDay())
            putInt(KEY_LEVEL, level.value)
        }
        state.value = Stored(day.toEpochDay(), level)
    }

    private fun read(): Stored? {
        if (!prefs.contains(KEY_DAY) || !prefs.contains(KEY_LEVEL)) return null
        val level = EnergyLevel.entries.firstOrNull { it.value == prefs.getInt(KEY_LEVEL, -1) } ?: return null
        return Stored(prefs.getLong(KEY_DAY, 0), level)
    }

    private companion object {
        const val FILE = "daily_energy"
        const val KEY_DAY = "day"
        const val KEY_LEVEL = "level"
    }
}
