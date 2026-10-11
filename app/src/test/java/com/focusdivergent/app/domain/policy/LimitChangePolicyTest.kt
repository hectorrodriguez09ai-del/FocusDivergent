package com.focusdivergent.app.domain.policy

import com.focusdivergent.app.domain.model.AppLimit
import com.focusdivergent.app.domain.model.FrictionLevel
import com.focusdivergent.app.domain.model.PendingLimitChange
import com.focusdivergent.app.domain.model.appLimitOf
import com.focusdivergent.app.domain.model.friction
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitChangePolicyTest {

    private val pkg = "com.example.social"
    private val midnight = Instant.parse("2026-10-11T00:00:00Z")

    private fun limit(minutes: Int, friction: FrictionLevel) = appLimitOf(pkg, minutes, friction)
    private fun plan(current: AppLimit?, desired: AppLimit?) = LimitChangePolicy.plan(pkg, current, desired, midnight)

    // ---- Niveles de fricción

    @Test
    fun `cada nivel de friccion se traduce a bloqueo estricto y ampliaciones`() {
        val flexible = limit(30, FrictionLevel.FLEXIBLE)
        val firm = limit(30, FrictionLevel.FIRM)
        val closed = limit(30, FrictionLevel.CLOSED)

        assertEquals(FrictionLevel.FLEXIBLE, flexible.friction)
        assertEquals(3, flexible.extensionsPerDay)
        assertFalse(flexible.isStrictlyBlocked)

        assertEquals(FrictionLevel.FIRM, firm.friction)
        assertEquals(0, firm.extensionsPerDay)
        assertFalse(firm.isStrictlyBlocked)

        assertEquals(FrictionLevel.CLOSED, closed.friction)
        assertEquals(0, closed.extensionsPerDay)
        assertTrue(closed.isStrictlyBlocked)
    }

    @Test
    fun `un limite sin nivel explicito es flexible, como los de antes de existir los niveles`() {
        assertEquals(FrictionLevel.FLEXIBLE, AppLimit(pkg, 30).friction)
        assertEquals(FrictionLevel.CLOSED, AppLimit(pkg, 30, isStrictlyBlocked = true).friction)
    }

    // ---- Qué permite cada limite

    @Test
    fun `una app cerrada no permite ni minutos ni ampliaciones, guarde los que guarde`() {
        val closed = limit(90, FrictionLevel.CLOSED)

        assertEquals(0, LimitChangePolicy.allowedMinutes(closed))
        assertEquals(0, LimitChangePolicy.extensions(closed))
        assertEquals(30, LimitChangePolicy.allowedMinutes(limit(30, FrictionLevel.FIRM)))
        assertEquals(3, LimitChangePolicy.extensions(limit(30, FrictionLevel.FLEXIBLE)))
    }

    // ---- Endurecer o relajar

    @Test
    fun `mas minutos relaja y menos minutos endurece`() {
        val base = limit(30, FrictionLevel.FLEXIBLE)

        assertTrue(LimitChangePolicy.isRelaxation(base, limit(60, FrictionLevel.FLEXIBLE)))
        assertFalse(LimitChangePolicy.isRelaxation(base, limit(15, FrictionLevel.FLEXIBLE)))
        assertFalse(LimitChangePolicy.isRelaxation(base, limit(30, FrictionLevel.FLEXIBLE)))
    }

    @Test
    fun `menos friccion relaja y mas friccion endurece`() {
        val flexible = limit(30, FrictionLevel.FLEXIBLE)
        val firm = limit(30, FrictionLevel.FIRM)
        val closed = limit(30, FrictionLevel.CLOSED)

        assertTrue(LimitChangePolicy.isRelaxation(firm, flexible))
        assertTrue(LimitChangePolicy.isRelaxation(closed, firm))
        assertTrue(LimitChangePolicy.isRelaxation(closed, flexible))
        assertFalse(LimitChangePolicy.isRelaxation(flexible, firm))
        assertFalse(LimitChangePolicy.isRelaxation(firm, closed))
        assertFalse(LimitChangePolicy.isRelaxation(flexible, closed))
    }

    @Test
    fun `pasar de cerrada a firme relaja aunque los minutos guardados fueran los mismos`() {
        // Cerrada permite 0 minutos; Firme de 30 permite 30. Los 30 que guardaba la cerrada no cuentan.
        assertTrue(LimitChangePolicy.isRelaxation(limit(30, FrictionLevel.CLOSED), limit(30, FrictionLevel.FIRM)))
        assertTrue(LimitChangePolicy.isRelaxation(limit(120, FrictionLevel.CLOSED), limit(15, FrictionLevel.FIRM)))
    }

    @Test
    fun `dos limites cerrados son equivalentes, y los demas lo son si coinciden en minutos y ampliaciones`() {
        assertTrue(LimitChangePolicy.sameRules(limit(30, FrictionLevel.CLOSED), limit(90, FrictionLevel.CLOSED)))
        assertTrue(LimitChangePolicy.sameRules(limit(30, FrictionLevel.FIRM), limit(30, FrictionLevel.FIRM)))
        assertFalse(LimitChangePolicy.sameRules(limit(30, FrictionLevel.FIRM), limit(30, FrictionLevel.FLEXIBLE)))
        assertFalse(LimitChangePolicy.sameRules(limit(30, FrictionLevel.FIRM), limit(45, FrictionLevel.FIRM)))
        assertFalse(LimitChangePolicy.sameRules(limit(30, FrictionLevel.FIRM), limit(30, FrictionLevel.CLOSED)))
    }

    // ---- Plan

    @Test
    fun `un limite nuevo se aplica al momento`() {
        val desired = limit(30, FrictionLevel.FLEXIBLE)

        assertEquals(LimitChangePlan.ApplyNow(desired), plan(current = null, desired = desired))
    }

    @Test
    fun `sin limite y sin pedir nada, no hay nada que hacer`() {
        assertEquals(LimitChangePlan.NoChange, plan(null, null))
    }

    @Test
    fun `pedir el limite que ya hay no cambia nada`() {
        val same = limit(30, FrictionLevel.FLEXIBLE)

        assertEquals(LimitChangePlan.NoChange, plan(same, same.copy()))
    }

    @Test
    fun `endurecer se aplica ahora`() {
        val current = limit(60, FrictionLevel.FLEXIBLE)

        assertEquals(LimitChangePlan.ApplyNow(limit(30, FrictionLevel.FLEXIBLE)), plan(current, limit(30, FrictionLevel.FLEXIBLE)))
        assertEquals(LimitChangePlan.ApplyNow(limit(60, FrictionLevel.FIRM)), plan(current, limit(60, FrictionLevel.FIRM)))
        assertEquals(LimitChangePlan.ApplyNow(limit(60, FrictionLevel.CLOSED)), plan(current, limit(60, FrictionLevel.CLOSED)))
        assertEquals(LimitChangePlan.ApplyNow(limit(15, FrictionLevel.FIRM)), plan(current, limit(15, FrictionLevel.FIRM)))
    }

    @Test
    fun `relajar espera a medianoche y el limite actual sigue como esta`() {
        val current = limit(30, FrictionLevel.FIRM)
        val desired = limit(60, FrictionLevel.FIRM)

        val result = plan(current, desired) as LimitChangePlan.Schedule

        assertNull(result.applyNow)
        assertEquals(PendingLimitChange(pkg, desired, midnight), result.pending)
    }

    @Test
    fun `quitar el limite es inmediato, no espera a medianoche, sea cual sea su nivel`() {
        for (friction in FrictionLevel.entries) {
            assertEquals(friction.name, LimitChangePlan.Remove, plan(limit(30, friction), desired = null))
        }
    }

    @Test
    fun `quitar el limite de una app que no tiene ninguno no hace nada`() {
        assertEquals(LimitChangePlan.NoChange, plan(current = null, desired = null))
    }

    @Test
    fun `un cambio mixto aplica ya lo que endurece y deja para medianoche el resultado completo`() {
        // Menos minutos (endurece) pero con ampliaciones donde antes no había (relaja).
        val current = limit(60, FrictionLevel.FIRM)
        val desired = limit(30, FrictionLevel.FLEXIBLE)

        val result = plan(current, desired) as LimitChangePlan.Schedule

        assertEquals(30, result.applyNow?.dailyMinutesAllowed)
        assertEquals(FrictionLevel.FIRM, result.applyNow?.friction)
        assertEquals(desired, result.pending.newLimit)
        assertEquals(midnight, result.pending.appliesAt)
    }

    @Test
    fun `lo que endurece de un cambio mixto nunca da mas margen que el limite actual`() {
        val current = limit(30, FrictionLevel.FLEXIBLE)
        val combos = listOf(15, 30, 60).flatMap { m -> FrictionLevel.entries.map { limit(m, it) } }

        for (desired in combos) {
            val tightened = LimitChangePolicy.tightenedPart(current, desired)
            assertFalse("$desired", LimitChangePolicy.isRelaxation(current, tightened))
        }
    }

    @Test
    fun `desde cerrada, cualquier cosa que abra espera a medianoche y la app sigue cerrada hoy`() {
        val closed = limit(30, FrictionLevel.CLOSED)

        for (desired in listOf(limit(15, FrictionLevel.FIRM), limit(60, FrictionLevel.FLEXIBLE))) {
            val result = plan(closed, desired) as LimitChangePlan.Schedule
            assertNull("sigue como está", result.applyNow)
            assertEquals(desired, result.pending.newLimit)
        }
        // Y desde cerrada, volver a cerrar no cambia nada aunque cambie el valor guardado.
        assertEquals(LimitChangePlan.NoChange, plan(closed, limit(90, FrictionLevel.CLOSED)))
    }

    // ---- Apps de ocio (Cierre de Jornada)

    private fun leisure(minutes: Int, friction: FrictionLevel, isLeisure: Boolean = true) = appLimitOf(pkg, minutes, friction, isLeisure)

    @Test
    fun `marcar una app como de ocio relaja y desmarcarla endurece`() {
        val normal = leisure(30, FrictionLevel.FLEXIBLE, isLeisure = false)
        val marked = leisure(30, FrictionLevel.FLEXIBLE)

        assertTrue(LimitChangePolicy.isRelaxation(normal, marked))
        assertFalse(LimitChangePolicy.isRelaxation(marked, normal))
    }

    @Test
    fun `marcar una app como de ocio espera a medianoche y mientras tanto no lo es`() {
        val current = leisure(30, FrictionLevel.FIRM, isLeisure = false)
        val desired = leisure(30, FrictionLevel.FIRM)

        val result = plan(current, desired) as LimitChangePlan.Schedule

        assertNull("sigue como está", result.applyNow)
        assertEquals(PendingLimitChange(pkg, desired, midnight), result.pending)
    }

    @Test
    fun `desmarcar una app de ocio rige al momento`() {
        val current = leisure(30, FrictionLevel.FIRM)
        val desired = leisure(30, FrictionLevel.FIRM, isLeisure = false)

        assertEquals(LimitChangePlan.ApplyNow(desired), plan(current, desired))
    }

    @Test
    fun `una app nueva ya marcada de ocio se crea al momento, pero sin ocio hasta medianoche no hace falta`() {
        // Crear un límite endurece, aunque nazca con la marca: la marca solo actúa de noche con el cierre.
        val desired = leisure(30, FrictionLevel.FLEXIBLE)

        assertEquals(LimitChangePlan.ApplyNow(desired), plan(current = null, desired = desired))
    }

    @Test
    fun `endurecer los minutos y marcar de ocio a la vez aplica ya lo primero y deja la marca para medianoche`() {
        val current = leisure(60, FrictionLevel.FIRM, isLeisure = false)
        val desired = leisure(30, FrictionLevel.FIRM)

        val result = plan(current, desired) as LimitChangePlan.Schedule

        assertEquals(leisure(30, FrictionLevel.FIRM, isLeisure = false), result.applyNow)
        assertEquals(desired, result.pending.newLimit)
    }

    @Test
    fun `la marca de ocio cuenta como diferencia aunque lo demas sea igual, tambien en apps cerradas`() {
        assertFalse(LimitChangePolicy.sameRules(leisure(30, FrictionLevel.FIRM), leisure(30, FrictionLevel.FIRM, isLeisure = false)))
        assertFalse(LimitChangePolicy.sameRules(leisure(30, FrictionLevel.CLOSED), leisure(90, FrictionLevel.CLOSED, isLeisure = false)))
        assertTrue(LimitChangePolicy.sameRules(leisure(30, FrictionLevel.CLOSED), leisure(90, FrictionLevel.CLOSED)))
    }

    @Test
    fun `lo que endurece de un cambio nunca da mas margen de noche que el limite actual`() {
        for (currentLeisure in listOf(true, false)) for (desiredLeisure in listOf(true, false)) {
            val current = leisure(30, FrictionLevel.FLEXIBLE, currentLeisure)
            val desired = leisure(15, FrictionLevel.FLEXIBLE, desiredLeisure)

            val tightened = LimitChangePolicy.tightenedPart(current, desired)

            assertFalse("$currentLeisure -> $desiredLeisure", LimitChangePolicy.isRelaxation(current, tightened))
            assertEquals(currentLeisure && desiredLeisure, tightened.isLeisure)
        }
    }
}
