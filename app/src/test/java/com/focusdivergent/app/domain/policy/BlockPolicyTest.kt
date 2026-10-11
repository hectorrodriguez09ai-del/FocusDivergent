package com.focusdivergent.app.domain.policy

import com.focusdivergent.app.domain.model.AppLimit
import com.focusdivergent.app.domain.model.BlockReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlockPolicyTest {

    private val social = AppLimit(packageName = "com.example.social", dailyMinutesAllowed = 30)

    @Test
    fun `por debajo del limite no se bloquea`() {
        assertNull(BlockPolicy.evaluate(social, usedMillis = 29 * 60_000L + 59_999L))
    }

    @Test
    fun `al llegar exactamente al limite se bloquea`() {
        assertEquals(
            BlockReason.LimitReached(usedMillis = 30 * 60_000L, allowedMillis = 30 * 60_000L),
            BlockPolicy.evaluate(social, usedMillis = 30 * 60_000L),
        )
    }

    @Test
    fun `pasado el limite se bloquea y recuerda lo usado`() {
        assertEquals(
            BlockReason.LimitReached(usedMillis = 45 * 60_000L, allowedMillis = 30 * 60_000L),
            BlockPolicy.evaluate(social, usedMillis = 45 * 60_000L),
        )
    }

    @Test
    fun `el bloqueo estricto bloquea aunque no se haya usado nada`() {
        val strict = social.copy(isStrictlyBlocked = true)

        assertEquals(BlockReason.StrictlyBlocked, BlockPolicy.evaluate(strict, usedMillis = 0))
    }

    @Test
    fun `los minutos extra se suman al limite diario`() {
        // 30 + 15 = 45 minutos permitidos.
        assertNull(BlockPolicy.evaluate(social, usedMillis = 44 * 60_000L, extraMinutes = 15))
        assertEquals(
            BlockReason.LimitReached(usedMillis = 45 * 60_000L, allowedMillis = 45 * 60_000L),
            BlockPolicy.evaluate(social, usedMillis = 45 * 60_000L, extraMinutes = 15),
        )
    }

    @Test
    fun `los minutos extra no desbloquean una app estricta`() {
        val strict = social.copy(isStrictlyBlocked = true)

        assertEquals(BlockReason.StrictlyBlocked, BlockPolicy.evaluate(strict, usedMillis = 0, extraMinutes = 45))
    }

    @Test
    fun `un limite de cero minutos bloquea desde el primer momento`() {
        val none = social.copy(dailyMinutesAllowed = 0)

        assertEquals(BlockReason.LimitReached(0, 0), BlockPolicy.evaluate(none, usedMillis = 0))
    }
}
