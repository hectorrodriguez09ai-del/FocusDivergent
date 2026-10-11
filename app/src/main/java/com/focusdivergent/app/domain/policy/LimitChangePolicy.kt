package com.focusdivergent.app.domain.policy

import com.focusdivergent.app.domain.model.AppLimit
import com.focusdivergent.app.domain.model.PendingLimitChange
import java.time.Instant

/** Qué hay que hacer con un cambio de límite que pide el usuario. */
sealed interface LimitChangePlan {

    /** No cambia nada (el límite pedido ya es el actual). */
    data object NoChange : LimitChangePlan

    /** Todo el cambio endurece (o el límite es nuevo): entra en vigor ya. */
    data class ApplyNow(val limit: AppLimit) : LimitChangePlan

    /**
     * Quitar el límite: se quita en el acto. No espera a medianoche, pero la pantalla lo protege con el reto
     * matemático (ver [LimitChangePolicy]).
     */
    data object Remove : LimitChangePlan

    /**
     * Algo del cambio relaja: [pending] entra en vigor más tarde. Si además hay una parte que endurece,
     * [applyNow] es el límite que rige mientras tanto; si no, el actual sigue como está.
     */
    data class Schedule(val applyNow: AppLimit?, val pending: PendingLimitChange) : LimitChangePlan
}

/**
 * Regla de los cambios de límite: **endurecer es inmediato, relajar espera a medianoche**.
 *
 * Sin esto, la pantalla de límites sería una puerta trasera: quien está bloqueado y quiere seguir subiría el
 * límite en un momento y anularía toda la fricción del motor de bloqueo.
 *
 * Un límite permite tres cosas: [allowedMinutes] de uso al día, [extensions] ampliaciones al día y, si es «de ocio»,
 * quedar libre de noche con el Cierre de Jornada. Un cambio relaja si sube cualquiera de las tres, y endurece si las
 * deja igual o más bajas. Crear un límite endurece.
 *
 * **Excepción deliberada: quitar un límite es inmediato**, pero solo tras resolver el reto matemático (lo exige
 * el editor, igual que ganar tiempo extra). Lo decidió el usuario: borrar un límite que ya no quiere no debe
 * obligarle a esperar al día siguiente. Tiene un coste que conviene tener presente: quitar un límite y volver a
 * crearlo más holgado equivale a relajarlo en el acto, con el reto como único freno.
 */
object LimitChangePolicy {

    /** Minutos de uso diario que permite el límite: una app cerrada no permite ninguno. */
    fun allowedMinutes(limit: AppLimit): Int = if (limit.isStrictlyBlocked) 0 else limit.dailyMinutesAllowed

    /** Ampliaciones diarias que permite el límite: una app cerrada no permite ninguna. */
    fun extensions(limit: AppLimit): Int = if (limit.isStrictlyBlocked) 0 else limit.extensionsPerDay

    /** `true` si [desired] permite más que [current] en algo: más minutos, más ampliaciones o quedar libre de noche. */
    fun isRelaxation(current: AppLimit, desired: AppLimit): Boolean =
        allowedMinutes(desired) > allowedMinutes(current) ||
            extensions(desired) > extensions(current) ||
            (desired.isLeisure && !current.isLeisure)

    /** Las dos reglas son equivalentes: una app cerrada lo es sin importar los minutos que guarde. */
    fun sameRules(a: AppLimit, b: AppLimit): Boolean =
        a.isStrictlyBlocked == b.isStrictlyBlocked &&
            a.isLeisure == b.isLeisure &&
            (a.isStrictlyBlocked || (a.dailyMinutesAllowed == b.dailyMinutesAllowed && a.extensionsPerDay == b.extensionsPerDay))

    /** Lo que quedaría de [desired] si solo se aplicara lo que endurece respecto a [current]. */
    fun tightenedPart(current: AppLimit, desired: AppLimit): AppLimit = AppLimit(
        packageName = current.packageName,
        dailyMinutesAllowed = minOf(current.dailyMinutesAllowed, desired.dailyMinutesAllowed),
        isStrictlyBlocked = current.isStrictlyBlocked || desired.isStrictlyBlocked,
        extensionsPerDay = minOf(current.extensionsPerDay, desired.extensionsPerDay),
        isLeisure = current.isLeisure && desired.isLeisure,
    )

    /**
     * Decide cómo aplicar el cambio de [current] a [desired]. [desired] nulo significa quitar el límite (en
     * el acto); [current] nulo, que la app aún no tiene. [midnight] es cuándo entran en vigor las relajaciones.
     */
    fun plan(packageName: String, current: AppLimit?, desired: AppLimit?, midnight: Instant): LimitChangePlan {
        if (current == null) return if (desired == null) LimitChangePlan.NoChange else LimitChangePlan.ApplyNow(desired)
        if (desired == null) return LimitChangePlan.Remove
        if (sameRules(current, desired)) return LimitChangePlan.NoChange

        val tightened = tightenedPart(current, desired)
        if (sameRules(tightened, desired)) return LimitChangePlan.ApplyNow(desired)

        return LimitChangePlan.Schedule(
            applyNow = tightened.takeUnless { sameRules(it, current) },
            pending = PendingLimitChange(packageName, desired, midnight),
        )
    }
}
