package com.focusdivergent.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Un movimiento del libro de XP y monedas (RF-6.1). El total se calcula sumando; no hay ningún contador.
 *
 * El índice único `(cause, ref_id, day)` es lo que impide que repetir una acción sume dos veces.
 *
 * @property cause nombre de `RewardCause`.
 * @property refId la actividad que lo causó (id de tarea, o instante de inicio de una sesión).
 * @property day día en que se ganó, en días desde 1970.
 */
@Entity(
    tableName = "xp_events",
    indices = [Index(value = ["cause", "ref_id", "day"], unique = true)],
)
data class XpEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cause: String,
    @ColumnInfo(name = "ref_id") val refId: Long,
    val day: Long,
    val xp: Int,
    val coins: Int,
    // El valor por defecto es el de los movimientos que ya existían antes de la migración 6→7.
    @ColumnInfo(defaultValue = "0") val minutes: Int = 0,
)
