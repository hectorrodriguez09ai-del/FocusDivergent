package com.focusdivergent.app.data.repository

import android.app.Application
import com.focusdivergent.app.domain.model.TimeExtension
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TimeExtensionRepositoryImplTest {

    private val social = "com.example.social"
    private val midnight = Instant.parse("2026-10-10T00:00:00Z")

    @Test
    fun `devuelve solo las ampliaciones de esa app desde la hora indicada, en orden`() = runTest {
        val db = createTestDatabase()
        try {
            val repository = TimeExtensionRepositoryImpl(db.timeExtensionDao())
            repository.add(TimeExtension(packageName = social, grantedAt = midnight.plusSeconds(7200), minutes = 15))
            repository.add(TimeExtension(packageName = social, grantedAt = midnight.minusSeconds(60), minutes = 15)) // ayer
            repository.add(TimeExtension(packageName = social, grantedAt = midnight.plusSeconds(3600), minutes = 15))
            repository.add(TimeExtension(packageName = "com.example.video", grantedAt = midnight.plusSeconds(3600), minutes = 15))

            val today = repository.getSince(social, since = midnight)

            assertEquals(
                listOf(midnight.plusSeconds(3600), midnight.plusSeconds(7200)),
                today.map { it.grantedAt },
            )
            assertEquals(listOf(social, social), today.map { it.packageName })
        } finally {
            db.close()
        }
    }

    @Test
    fun `add devuelve el id y los instantes sobreviven a la ida y vuelta`() = runTest {
        val db = createTestDatabase()
        try {
            val repository = TimeExtensionRepositoryImpl(db.timeExtensionDao())
            val at = Instant.parse("2026-10-10T08:30:15.123Z")

            val id = repository.add(TimeExtension(packageName = social, grantedAt = at, minutes = 15))

            val saved = repository.getSince(social, midnight).single()
            assertEquals(id, saved.id)
            assertEquals(at, saved.grantedAt)
            assertEquals(15, saved.minutes)
        } finally {
            db.close()
        }
    }
}
