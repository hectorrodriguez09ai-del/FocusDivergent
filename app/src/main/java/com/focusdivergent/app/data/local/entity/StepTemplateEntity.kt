package com.focusdivergent.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Plantilla de pasos del usuario. [steps] guarda un paso por línea: el texto de un paso nunca lleva saltos de línea
 * (ver `StepRules.cleanStep`), así que no hace falta nada más elaborado.
 */
@Entity(tableName = "step_templates")
data class StepTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val steps: String,
)
