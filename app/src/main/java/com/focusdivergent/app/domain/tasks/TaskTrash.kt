package com.focusdivergent.app.domain.tasks

import com.focusdivergent.app.domain.model.Task
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * La última actividad borrada, para poder deshacerlo durante unos segundos.
 *
 * Solo vive en memoria y solo guarda una: borrar no pide confirmación (menos fricción), pero tampoco es
 * definitivo al instante. Vive fuera de las pantallas porque se borra en el editor y se deshace en la lista.
 */
@Singleton
class TaskTrash @Inject constructor() {

    private val _lastDeleted = MutableStateFlow<Task?>(null)
    val lastDeleted: StateFlow<Task?> = _lastDeleted.asStateFlow()

    fun put(task: Task) {
        _lastDeleted.value = task
    }

    /** Vacía la papelera, pero solo si sigue conteniendo [task]: así un aviso antiguo no borra uno más reciente. */
    fun clear(task: Task) {
        _lastDeleted.compareAndSet(task, null)
    }
}
