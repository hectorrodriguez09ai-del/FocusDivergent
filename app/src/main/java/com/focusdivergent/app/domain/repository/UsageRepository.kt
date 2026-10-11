package com.focusdivergent.app.domain.repository

import com.focusdivergent.app.domain.model.UsageSnapshot

/** Acceso al tiempo de uso de las apps del dispositivo. */
interface UsageRepository {

    /** Lee el uso de hoy y la app en primer plano. Sin el permiso de uso de apps devuelve una lectura vacía. */
    suspend fun readToday(): UsageSnapshot
}
