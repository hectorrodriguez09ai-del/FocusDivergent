package com.focusdivergent.app.presentation.mascot

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.focusdivergent.app.R
import com.focusdivergent.app.domain.reward.Cosmetic
import com.focusdivergent.app.domain.reward.Mascot
import com.focusdivergent.app.domain.reward.MascotStages

@StringRes
fun Mascot.labelRes(): Int = when (this) {
    Mascot.FOX -> R.string.mascot_fox
    Mascot.CAT -> R.string.mascot_cat
    Mascot.ALIEN -> R.string.mascot_alien
}

@ArrayRes
private fun Mascot.stagesRes(): Int = when (this) {
    Mascot.FOX -> R.array.mascot_fox_stages
    Mascot.CAT -> R.array.mascot_cat_stages
    Mascot.ALIEN -> R.array.mascot_alien_stages
}

/** El nombre de la etapa [stage] (de 1 a [MascotStages.COUNT]) de esta mascota: «Cachorro», «Con pañuelo»… */
@Composable
fun Mascot.stageName(stage: Int): String =
    stringArrayResource(stagesRes()).let { it[stage.coerceIn(1, MascotStages.COUNT) - 1] }

@StringRes
fun Cosmetic.labelRes(): Int = when (this) {
    Cosmetic.SCENERY_CALM -> R.string.cosmetic_scenery_calm
    Cosmetic.SCENERY_NIGHT -> R.string.cosmetic_scenery_night
    Cosmetic.SCENERY_FOREST -> R.string.cosmetic_scenery_forest
    Cosmetic.SCENERY_DUSK -> R.string.cosmetic_scenery_dusk
    Cosmetic.SCENERY_NEBULA -> R.string.cosmetic_scenery_nebula
    Cosmetic.PROP_PLANT -> R.string.cosmetic_prop_plant
    Cosmetic.PROP_BOOKS -> R.string.cosmetic_prop_books
    Cosmetic.PROP_LAMP -> R.string.cosmetic_prop_lamp
    Cosmetic.PROP_STAR -> R.string.cosmetic_prop_star
}

/** «Zorro, con pañuelo»: lo que lee un lector de pantalla para la imagen. */
@Composable
fun mascotImageDescription(mascot: Mascot, stage: Int): String =
    stringResource(R.string.mascot_stage_description, stringResource(mascot.labelRes()), mascot.stageName(stage))
