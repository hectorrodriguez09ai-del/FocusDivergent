package com.focusdivergent.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Lo del progreso que no se puede calcular a partir de otras tablas: la mejor racha (RF-6.1). El XP y las monedas
 * salen del libro `xp_events`.
 *
 * La tabla tiene una única fila, con id [SINGLETON_ID], que se crea junto con la base de datos.
 */
@Entity(tableName = "user_stats")
data class UserStatsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "best_streak", defaultValue = "0") val bestStreak: Int = 0,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
