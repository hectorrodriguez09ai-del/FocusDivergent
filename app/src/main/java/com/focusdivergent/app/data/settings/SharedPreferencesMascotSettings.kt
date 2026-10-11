package com.focusdivergent.app.data.settings

import android.content.Context
import androidx.core.content.edit
import com.focusdivergent.app.domain.reward.Cosmetic
import com.focusdivergent.app.domain.reward.CosmeticKind
import com.focusdivergent.app.domain.reward.Mascot
import com.focusdivergent.app.domain.reward.MascotSelection
import com.focusdivergent.app.domain.reward.MascotSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Recuerda qué mascota, fondo y objeto lleva el usuario. Lo que posee se guarda aparte, en la base de datos. */
@Singleton
class SharedPreferencesMascotSettings @Inject constructor(
    @ApplicationContext context: Context,
) : MascotSettings {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    override val selection: StateFlow<MascotSelection> = state.asStateFlow()

    override fun update(change: (MascotSelection) -> MascotSelection) {
        val updated = change(state.value)
        prefs.edit {
            putString(KEY_MASCOT, updated.mascot.name)
            putString(KEY_SCENERY, updated.scenery.name)
            if (updated.prop == null) remove(KEY_PROP) else putString(KEY_PROP, updated.prop.name)
        }
        state.value = updated
    }

    private fun read(): MascotSelection {
        val defaults = MascotSelection()
        // Un valor desconocido (o de otro tipo de cosmético) vuelve al de serie en vez de romper la lectura.
        return MascotSelection(
            mascot = Mascot.entries.firstOrNull { it.name == prefs.getString(KEY_MASCOT, null) } ?: defaults.mascot,
            scenery = cosmetic(KEY_SCENERY, CosmeticKind.SCENERY) ?: defaults.scenery,
            prop = cosmetic(KEY_PROP, CosmeticKind.PROP),
        )
    }

    private fun cosmetic(key: String, kind: CosmeticKind): Cosmetic? =
        Cosmetic.entries.firstOrNull { it.name == prefs.getString(key, null) && it.kind == kind }

    private companion object {
        const val FILE = "mascot"
        const val KEY_MASCOT = "mascot"
        const val KEY_SCENERY = "scenery"
        const val KEY_PROP = "prop"
    }
}
