package com.focusdivergent.app.domain.reward

/** Las mascotas entre las que se elige (RF-6.1). El progreso es del usuario, no de la mascota: se puede cambiar sin perder nada. */
enum class Mascot { FOX, CAT, ALIEN }

enum class CosmeticKind {
    /** El fondo sobre el que está la mascota. Siempre hay uno puesto. */
    SCENERY,

    /** Un objeto que se pone junto a la mascota. Se puede no llevar ninguno. */
    PROP,
}

/**
 * Lo que se compra con monedas de enfoque. Los cosméticos van **junto** a la mascota, no encima, así que valen
 * igual para cualquier mascota en cualquier etapa.
 */
enum class Cosmetic(val kind: CosmeticKind, val price: Int) {
    SCENERY_CALM(CosmeticKind.SCENERY, 0),
    SCENERY_NIGHT(CosmeticKind.SCENERY, 6),
    SCENERY_FOREST(CosmeticKind.SCENERY, 10),
    SCENERY_DUSK(CosmeticKind.SCENERY, 14),
    SCENERY_NEBULA(CosmeticKind.SCENERY, 20),

    PROP_PLANT(CosmeticKind.PROP, 5),
    PROP_BOOKS(CosmeticKind.PROP, 8),
    PROP_LAMP(CosmeticKind.PROP, 12),
    PROP_STAR(CosmeticKind.PROP, 16),
    ;

    val isFree: Boolean get() = price == 0
}

/** Qué mascota, fondo y objeto lleva el usuario puestos. */
data class MascotSelection(
    val mascot: Mascot = Mascot.FOX,
    val scenery: Cosmetic = Cosmetic.SCENERY_CALM,
    val prop: Cosmetic? = null,
) {
    /** Pone [cosmetic] donde le toca (fondo u objeto). */
    fun wearing(cosmetic: Cosmetic): MascotSelection = when (cosmetic.kind) {
        CosmeticKind.SCENERY -> copy(scenery = cosmetic)
        CosmeticKind.PROP -> copy(prop = cosmetic)
    }
}

/** Una compra hecha: la moneda gastada se descuenta del saldo. */
data class CosmeticPurchase(
    val cosmetic: Cosmetic,
    val price: Int,
)

/** Las cinco etapas de cada mascota, que se alcanzan al llegar a ciertos niveles. */
object MascotStages {
    const val COUNT = 5

    /** El nivel con el que empieza cada etapa. */
    val LEVELS: List<Int> = listOf(1, 3, 6, 10, 15)

    /** La etapa (de 1 a [COUNT]) que corresponde a [level]. */
    fun stageOf(level: Int): Int = LEVELS.indexOfLast { level >= it }.coerceAtLeast(0) + 1
}
