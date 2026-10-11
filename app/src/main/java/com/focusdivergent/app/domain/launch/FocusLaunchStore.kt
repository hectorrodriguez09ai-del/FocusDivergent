package com.focusdivergent.app.domain.launch

import com.focusdivergent.app.domain.model.FocusLaunchSession

/**
 * La Lanzadera en curso, si hay alguna. Se guarda en disco: si el sistema mata el servicio de bloqueo o el usuario
 * apaga la pantalla, la sesión sigue, porque lo que manda es la hora a la que termina y no un contador en memoria.
 */
interface FocusLaunchStore {

    /** La sesión en curso (o ya terminada pero aún sin contestar a «¿quieres seguir?»), o nulo. */
    fun current(): FocusLaunchSession?

    fun save(session: FocusLaunchSession)

    fun clear()
}
