package com.focusdivergent.app.data.repository

import android.app.Application
import com.focusdivergent.app.data.local.FocusDatabase
import com.focusdivergent.app.data.mapper.toDomain
import com.focusdivergent.app.data.mapper.toEntity
import com.focusdivergent.app.domain.model.AppLimit
import com.focusdivergent.app.domain.model.PendingLimitChange
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PendingLimitChangeRepositoryImplTest {

    private lateinit var db: FocusDatabase
    private lateinit var repository: PendingLimitChangeRepositoryImpl

    private val midnight = Instant.parse("2026-10-11T00:00:00Z")
    private val social = "com.example.social"

    @Before
    fun setUp() {
        db = createTestDatabase()
        repository = PendingLimitChangeRepositoryImpl(db.pendingLimitChangeDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `un cambio de limite se guarda y se lee con todos sus campos`() = runTest {
        val change = PendingLimitChange(social, AppLimit(social, 60, isStrictlyBlocked = false, extensionsPerDay = 3), midnight)

        repository.save(change)

        assertEquals(change, repository.get(social))
        assertEquals(listOf(change), repository.observeAll().first())
    }

    @Test
    fun `quitar el limite se guarda como un cambio sin limite nuevo`() = runTest {
        repository.save(PendingLimitChange(social, newLimit = null, appliesAt = midnight))

        val stored = repository.get(social)!!

        assertNull(stored.newLimit)
        assertTrue(stored.isRemoval)
        assertEquals(midnight, stored.appliesAt)
    }

    @Test
    fun `solo hay un cambio por app, el nuevo sustituye al anterior`() = runTest {
        repository.save(PendingLimitChange(social, AppLimit(social, 60), midnight))
        repository.save(PendingLimitChange(social, AppLimit(social, 90, extensionsPerDay = 0), midnight.plusSeconds(60)))

        val all = repository.observeAll().first()

        assertEquals(1, all.size)
        assertEquals(90, all.single().newLimit?.dailyMinutesAllowed)
    }

    @Test
    fun `getDue devuelve solo los que ya han llegado a su hora, el instante exacto incluido`() = runTest {
        repository.save(PendingLimitChange("com.example.a", AppLimit("com.example.a", 30), midnight.minusSeconds(1)))
        repository.save(PendingLimitChange("com.example.b", AppLimit("com.example.b", 30), midnight))
        repository.save(PendingLimitChange("com.example.c", AppLimit("com.example.c", 30), midnight.plusSeconds(1)))

        val due = repository.getDue(midnight).map { it.packageName }

        assertEquals(listOf("com.example.a", "com.example.b"), due.sorted())
    }

    @Test
    fun `borrar un cambio no toca los de otras apps`() = runTest {
        repository.save(PendingLimitChange("com.example.a", AppLimit("com.example.a", 30), midnight))
        repository.save(PendingLimitChange("com.example.b", AppLimit("com.example.b", 30), midnight))

        repository.delete("com.example.a")

        assertNull(repository.get("com.example.a"))
        assertEquals(listOf("com.example.b"), repository.observeAll().first().map { it.packageName })
    }

    @Test
    fun `el mapeo conserva el cambio en la ida y la vuelta`() {
        val withLimit = PendingLimitChange(social, AppLimit(social, 45, true, 0), midnight)
        val removal = PendingLimitChange(social, null, midnight)

        assertEquals(withLimit, withLimit.toEntity().toDomain())
        assertEquals(removal, removal.toEntity().toDomain())
    }
}
