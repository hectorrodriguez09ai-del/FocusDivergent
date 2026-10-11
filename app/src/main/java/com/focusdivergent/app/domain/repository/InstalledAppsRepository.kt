package com.focusdivergent.app.domain.repository

import com.focusdivergent.app.domain.model.InstalledApp

/** Las apps del dispositivo, para elegir a cuáles ponerles límite. */
interface InstalledAppsRepository {

    /** Apps con icono de lanzador, sin duplicados y ordenadas por nombre. */
    suspend fun launchableApps(): List<InstalledApp>

    /** Nombre que ve el usuario para [packageName], o nulo si la app ya no está instalada. */
    suspend fun labelOf(packageName: String): String?
}
