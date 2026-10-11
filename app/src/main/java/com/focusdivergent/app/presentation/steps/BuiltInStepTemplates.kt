package com.focusdivergent.app.presentation.steps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.focusdivergent.app.R
import com.focusdivergent.app.domain.steps.StepTemplate

/**
 * Las plantillas de partida (RF-3.1). Son genéricas a propósito: ayudan a arrancar con cosas que cuestan de empezar,
 * y el usuario las completa o las cambia en su lista. Viven en los recursos de texto y no en la base de datos:
 * no se pueden borrar ni cambiar, y no ocupan sitio entre las plantillas propias.
 */
@Composable
fun rememberBuiltInStepTemplates(): List<StepTemplate> {
    val clean = stringResource(R.string.step_template_clean_name) to stringArrayResource(R.array.step_template_clean_steps)
    val work = stringResource(R.string.step_template_work_name) to stringArrayResource(R.array.step_template_work_steps)
    val study = stringResource(R.string.step_template_study_name) to stringArrayResource(R.array.step_template_study_steps)
    val errand = stringResource(R.string.step_template_errand_name) to stringArrayResource(R.array.step_template_errand_steps)
    return remember(clean, work, study, errand) {
        listOf(clean, work, study, errand).map { (name, steps) ->
            StepTemplate(name = name, steps = steps.toList(), builtIn = true)
        }
    }
}
