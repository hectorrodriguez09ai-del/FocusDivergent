package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.closure.ClosureWindow
import com.focusdivergent.app.domain.closure.DayClosureSettings
import com.focusdivergent.app.domain.model.BlockReason
import com.focusdivergent.app.domain.model.BlockedApp
import com.focusdivergent.app.domain.model.UsageSnapshot
import com.focusdivergent.app.domain.policy.BlockPolicy
import com.focusdivergent.app.domain.policy.ExtensionPolicy
import com.focusdivergent.app.domain.repository.AppLimitRepository
import com.focusdivergent.app.domain.repository.TimeExtensionRepository
import com.focusdivergent.app.domain.time.startOfToday
import java.time.Clock
import javax.inject.Inject
import javax.inject.Named

/**
 * Decide si la app que está en pantalla según [UsageSnapshot] debe cubrirse con la pantalla de pausa.
 *
 * La app propia nunca se bloquea: si lo hiciera, el usuario no podría llegar a los ajustes para
 * cambiar sus límites.
 */
class FindBlockedAppUseCase @Inject constructor(
    private val limits: AppLimitRepository,
    private val extensions: TimeExtensionRepository,
    private val closure: DayClosureSettings,
    private val clock: Clock,
    @Named("ownPackage") private val ownPackage: String,
) {

    suspend operator fun invoke(snapshot: UsageSnapshot): BlockedApp? {
        val packageName = snapshot.foregroundPackage ?: return null
        if (packageName == ownPackage) return null

        val limit = limits.getByPackage(packageName) ?: return null

        // Cierre de Jornada: de noche, entre la hora de cierre y la de la mañana, las apps de ocio quedan libres. Solo
        // se levanta este bloqueo; el aviso de Obligaciones, la Lanzadera y el Reality Check los decide quien llama.
        if (limit.isLeisure) {
            val now = clock.instant()
            if (ClosureWindow.isOpen(closure.state.value.configAt(now), now, clock.zone)) return null
        }

        val used = snapshot.usedMillisByPackage[packageName] ?: 0L

        // Las ampliaciones solo suman tiempo: si ni siquiera sin ellas se ha agotado, no hace falta consultarlas.
        if (BlockPolicy.evaluate(limit, used) == null) return null
        if (limit.isStrictlyBlocked) return BlockedApp(packageName, BlockReason.StrictlyBlocked, extensionsLeft = 0, extensionsPerDay = 0)

        val today = extensions.getSince(packageName, clock.startOfToday())
        val reason = BlockPolicy.evaluate(limit, used, extraMinutes = today.sumOf { it.minutes }) ?: return null
        return BlockedApp(packageName, reason, ExtensionPolicy.remaining(limit, today.size), limit.extensionsPerDay)
    }
}
