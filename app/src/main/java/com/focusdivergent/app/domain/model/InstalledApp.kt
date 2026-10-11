package com.focusdivergent.app.domain.model

/** Una app instalada que se puede abrir (tiene icono de lanzador), con el nombre que ve el usuario. */
data class InstalledApp(
    val packageName: String,
    val label: String,
)
