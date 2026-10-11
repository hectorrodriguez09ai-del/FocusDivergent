package com.focusdivergent.app.domain.settings

import kotlinx.coroutines.flow.StateFlow

/**
 * Si aún hay que ofrecer al usuario añadir el mosaico «Anotar idea» a los Ajustes rápidos. Se deja de ofrecer en
 * cuanto responde (lo añade, ya lo tenía o dice que no): preguntar dos veces sería insistir.
 */
interface CaptureTileOffer {

    val pending: StateFlow<Boolean>

    fun markHandled()
}
