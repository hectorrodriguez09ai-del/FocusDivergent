package com.focusdivergent.app.domain.repository

import com.focusdivergent.app.domain.model.AppLimit
import kotlinx.coroutines.flow.Flow

interface AppLimitRepository {

    fun observeAll(): Flow<List<AppLimit>>

    /** Límite configurado para una app, o nulo si no tiene. Lo consulta el servicio de bloqueo. */
    suspend fun getByPackage(packageName: String): AppLimit?

    /** Crea o sustituye el límite de la app. */
    suspend fun save(limit: AppLimit)

    suspend fun delete(limit: AppLimit)
}
