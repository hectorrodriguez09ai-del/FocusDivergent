package com.focusdivergent.app.domain.model

import java.time.Instant

/**
 * Un cambio de límite que todavía no ha entrado en vigor: relajar un límite (más minutos, menos fricción o
 * quitarlo) se aplica a medianoche, para que la decisión se tome en frío y no en el momento de estar bloqueado.
 *
 * @property newLimit cómo quedará el límite, o nulo si el cambio es quitarlo.
 * @property appliesAt cuándo entra en vigor.
 */
data class PendingLimitChange(
    val packageName: String,
    val newLimit: AppLimit?,
    val appliesAt: Instant,
) {
    val isRemoval: Boolean get() = newLimit == null
}
