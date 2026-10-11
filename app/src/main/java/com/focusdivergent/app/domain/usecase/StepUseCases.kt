package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.repository.StepTemplateRepository
import com.focusdivergent.app.domain.repository.TaskStepRepository
import com.focusdivergent.app.domain.reward.RewardLedger
import com.focusdivergent.app.domain.steps.StepRules
import com.focusdivergent.app.domain.steps.TaskStep
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Añade pasos al final de una tarea: sirve tanto para un paso escrito a mano como para aplicar una plantilla.
 * Quita los textos vacíos y respeta el máximo de pasos por tarea (los que sobren se dejan fuera, sin error).
 *
 * @return cuántos pasos se añadieron.
 */
class AddStepsUseCase @Inject constructor(
    private val steps: TaskStepRepository,
) {

    suspend operator fun invoke(taskId: Long, texts: List<String>): Int {
        val room = StepRules.MAX_STEPS_PER_TASK - steps.getForTask(taskId).size
        val clean = texts.mapNotNull(StepRules::cleanStep).take(room.coerceAtLeast(0))
        if (clean.isEmpty()) return 0
        steps.addAll(taskId, clean)
        return clean.size
    }
}

/**
 * Marca o desmarca un paso. Marcar suma su XP al libro; desmarcar lo retira, como con las tareas, así que marcar y
 * desmarcar no se puede usar para inflar nada.
 */
class SetStepDoneUseCase @Inject constructor(
    private val steps: TaskStepRepository,
    private val rewards: RewardLedger,
) {

    suspend operator fun invoke(step: TaskStep, done: Boolean) {
        steps.setDone(step.id, done)
        if (done) rewards.stepDone(step.id) else rewards.stepUndone(step.id)
    }
}

sealed interface SaveTemplateResult {
    data object Saved : SaveTemplateResult
    data object NeedsName : SaveTemplateResult
    data object NeedsSteps : SaveTemplateResult

    /** Ya hay el máximo de plantillas propias y ésta tendría un nombre nuevo. */
    data object TooMany : SaveTemplateResult
}

/** Guarda una lista de pasos como plantilla propia. Con un nombre que ya existe, la sustituye. */
class SaveStepTemplateUseCase @Inject constructor(
    private val templates: StepTemplateRepository,
) {

    suspend operator fun invoke(name: String, stepTexts: List<String>): SaveTemplateResult {
        val cleanName = StepRules.cleanName(name) ?: return SaveTemplateResult.NeedsName
        val cleanSteps = stepTexts.mapNotNull(StepRules::cleanStep).take(StepRules.MAX_STEPS_PER_TASK)
        if (cleanSteps.isEmpty()) return SaveTemplateResult.NeedsSteps
        val existing = templates.observeAll().first()
        val replaces = existing.any { it.name.equals(cleanName, ignoreCase = true) }
        if (!replaces && existing.size >= StepRules.MAX_USER_TEMPLATES) return SaveTemplateResult.TooMany
        templates.save(cleanName, cleanSteps)
        return SaveTemplateResult.Saved
    }
}
