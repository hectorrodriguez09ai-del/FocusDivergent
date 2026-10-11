package com.focusdivergent.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Idea capturada al vuelo (RF-5.1). Vive en la bandeja de entrada hasta que se procesa.
 *
 * [timestamp] es epoch en milisegundos.
 */
@Entity(
    tableName = "brain_dumps",
    indices = [Index("is_processed")],
)
data class BrainDumpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val timestamp: Long,
    @ColumnInfo(name = "is_processed") val isProcessed: Boolean = false,
)
