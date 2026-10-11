package com.focusdivergent.app.data.repository

import android.app.Application
import com.focusdivergent.app.domain.model.AppLimit
import com.focusdivergent.app.domain.model.BrainDump
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppLimitAndBrainDumpRepositoryTest {

    @Test
    fun `save sustituye el limite de la misma app y delete lo quita`() = runTest {
        val db = createTestDatabase()
        try {
            val repository = AppLimitRepositoryImpl(db.appLimitDao())
            val social = AppLimit(packageName = "com.example.social", dailyMinutesAllowed = 60)

            repository.save(social)
            repository.save(social.copy(dailyMinutesAllowed = 30, isStrictlyBlocked = true))

            assertEquals(
                AppLimit("com.example.social", dailyMinutesAllowed = 30, isStrictlyBlocked = true),
                repository.getByPackage("com.example.social"),
            )
            assertEquals(1, repository.observeAll().first().size)

            repository.delete(repository.getByPackage("com.example.social")!!)
            assertNull(repository.getByPackage("com.example.social"))
        } finally {
            db.close()
        }
    }

    @Test
    fun `las ampliaciones por dia se guardan y un limite nuevo sin indicarlas trae las tres de serie`() = runTest {
        val db = createTestDatabase()
        try {
            val repository = AppLimitRepositoryImpl(db.appLimitDao())

            repository.save(AppLimit("com.example.firm", dailyMinutesAllowed = 30, extensionsPerDay = 0))
            repository.save(AppLimit("com.example.default", dailyMinutesAllowed = 30))

            assertEquals(0, repository.getByPackage("com.example.firm")?.extensionsPerDay)
            assertEquals(3, repository.getByPackage("com.example.default")?.extensionsPerDay)
        } finally {
            db.close()
        }
    }

    @Test
    fun `una idea nueva entra en la bandeja y sale al procesarla`() = runTest {
        val db = createTestDatabase()
        try {
            val repository = BrainDumpRepositoryImpl(db.brainDumpDao())
            val when_ = Instant.parse("2026-10-09T22:30:00Z")

            val id = repository.add(BrainDump(content = "Mirar sintetizadores", timestamp = when_))

            val inbox = repository.observeUnprocessed().first()
            assertEquals(listOf(id), inbox.map { it.id })
            assertEquals(when_, inbox.single().timestamp)

            repository.markProcessed(id)
            assertTrue(repository.observeUnprocessed().first().isEmpty())
        } finally {
            db.close()
        }
    }
}
