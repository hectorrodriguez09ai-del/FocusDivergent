package com.focusdivergent.app.domain.vault

import com.focusdivergent.app.domain.model.VaultNode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * La última nota borrada, para poder deshacerlo durante unos segundos. Solo vive en memoria y solo guarda una.
 *
 * Vive fuera de las pantallas porque se borra en el editor y se deshace en la lista. Las carpetas no pasan por
 * aquí: arrastran todo su contenido y por eso piden confirmación en vez de ofrecer «Deshacer».
 */
@Singleton
class VaultTrash @Inject constructor() {

    private val _lastDeleted = MutableStateFlow<VaultNode?>(null)
    val lastDeleted: StateFlow<VaultNode?> = _lastDeleted.asStateFlow()

    fun put(note: VaultNode) {
        _lastDeleted.value = note
    }

    /** Vacía la papelera, pero solo si sigue conteniendo [note]: un aviso antiguo no borra uno más reciente. */
    fun clear(note: VaultNode) {
        _lastDeleted.compareAndSet(note, null)
    }
}
