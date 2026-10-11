package com.focusdivergent.app.presentation.block

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.focusdivergent.app.domain.model.BlockedApp
import com.focusdivergent.app.domain.model.FocusLaunch
import com.focusdivergent.app.domain.model.FocusLaunchPrompt
import com.focusdivergent.app.domain.model.Interruption
import com.focusdivergent.app.domain.model.ObligationReminder
import com.focusdivergent.app.domain.model.RealityCheck

/** Elige la pantalla que corresponde a cada tipo de [Interruption]. */
@Composable
fun InterruptionContent(
    interruption: Interruption,
    labelOf: (packageName: String) -> String,
    realityCheckSilenceMinutes: Int,
    onGoHome: () -> Unit,
    onEarnExtension: (packageName: String) -> Unit,
    onPostponeReminder: (taskId: Long) -> Unit,
    onContinueRealityCheck: (packageName: String) -> Unit,
    onLaunchFinished: () -> Unit,
    onLaunchContinue: (taskId: Long?) -> Unit,
    onLaunchStop: () -> Unit,
) {
    when (interruption) {
        is BlockedApp -> BlockOverlayContent(
            appLabel = remember(interruption.packageName) { labelOf(interruption.packageName) },
            reason = interruption.reason,
            extensionsLeft = interruption.extensionsLeft,
            extensionsPerDay = interruption.extensionsPerDay,
            onGoHome = onGoHome,
            onEarnExtension = { onEarnExtension(interruption.packageName) },
        )
        is ObligationReminder -> ObligationReminderContent(
            title = interruption.title,
            startTime = interruption.startTime,
            onPostpone = { onPostponeReminder(interruption.taskId) },
        )
        is FocusLaunch -> FocusLaunchContent(
            taskTitle = interruption.taskTitle,
            endsAt = interruption.endsAt,
            onFinished = onLaunchFinished,
        )
        is FocusLaunchPrompt -> FocusLaunchPromptContent(
            taskTitle = interruption.taskTitle,
            onContinue = { onLaunchContinue(interruption.taskId) },
            onStop = onLaunchStop,
        )
        is RealityCheck -> RealityCheckContent(
            appLabel = remember(interruption.packageName) { labelOf(interruption.packageName) },
            continuousMillis = interruption.continuousMillis,
            silenceMinutes = realityCheckSilenceMinutes,
            onContinue = { onContinueRealityCheck(interruption.packageName) },
            onGoHome = onGoHome,
        )
    }
}
