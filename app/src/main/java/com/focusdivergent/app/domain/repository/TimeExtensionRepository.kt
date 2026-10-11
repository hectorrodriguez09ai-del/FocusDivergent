package com.focusdivergent.app.domain.repository

import com.focusdivergent.app.domain.model.TimeExtension
import java.time.Instant

interface TimeExtensionRepository {

    /** Ampliaciones de una app concedidas desde [since], la más antigua primero. */
    suspend fun getSince(packageName: String, since: Instant): List<TimeExtension>

    /** Guarda una ampliación y devuelve su id. */
    suspend fun add(extension: TimeExtension): Long
}
