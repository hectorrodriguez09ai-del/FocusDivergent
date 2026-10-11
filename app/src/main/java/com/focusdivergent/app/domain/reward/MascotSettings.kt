package com.focusdivergent.app.domain.reward

import kotlinx.coroutines.flow.StateFlow

/** La mascota, el fondo y el objeto que el usuario lleva puestos. Se guarda en el móvil. */
interface MascotSettings {

    val selection: StateFlow<MascotSelection>

    fun update(change: (MascotSelection) -> MascotSelection)
}
