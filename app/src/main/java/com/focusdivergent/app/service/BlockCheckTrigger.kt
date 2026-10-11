package com.focusdivergent.app.service

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Aviso de "algo ha cambiado en pantalla, vuelve a comprobar".
 *
 * Lo emite el servicio de accesibilidad en cada cambio de ventana y lo consume el servicio de
 * bloqueo. Así la detección es casi instantánea sin tener que consultar el uso de apps en bucle
 * a gran velocidad. Las peticiones se funden: si llegan varias seguidas, se atiende una sola.
 */
@Singleton
class BlockCheckTrigger @Inject constructor() {

    private val channel = Channel<Unit>(Channel.CONFLATED)

    val events: Flow<Unit> = channel.receiveAsFlow()

    fun request() {
        channel.trySend(Unit)
    }
}
