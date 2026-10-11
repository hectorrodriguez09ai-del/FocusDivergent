package com.focusdivergent.app.domain.tasks

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurrenceTest {

    private val saturday = LocalDate.of(2026, 10, 10)
    private val monday = LocalDate.of(2026, 10, 12)

    @Test
    fun `diaria toca todos los dias`() {
        assertTrue((0L..13L).all { Recurrence.Daily.occursOn(saturday.plusDays(it)) })
        assertEquals("FREQ=DAILY", Recurrence.Daily.toRule())
    }

    @Test
    fun `semanal solo toca los dias marcados`() {
        val weekdays = Recurrence.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))

        assertFalse(weekdays.occursOn(saturday))
        assertTrue(weekdays.occursOn(monday))
        assertFalse(weekdays.occursOn(monday.plusDays(1)))
        assertTrue(weekdays.occursOn(monday.plusDays(2)))
    }

    @Test
    fun `la regla semanal lista los dias de lunes a domingo, se marquen en el orden que se marquen`() {
        val rule = Recurrence.Weekly(setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.THURSDAY)).toRule()

        assertEquals("FREQ=WEEKLY;BYDAY=MO,TH,SU", rule)
    }

    @Test
    fun `una regla guardada se lee igual que se escribio`() {
        val original = Recurrence.Weekly(setOf(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY))

        assertEquals(original, Recurrence.parse(original.toRule()))
        assertEquals(Recurrence.Daily, Recurrence.parse(Recurrence.Daily.toRule()))
    }

    @Test
    fun `se aceptan minusculas, espacios y el prefijo RRULE`() {
        assertEquals(
            Recurrence.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)),
            Recurrence.parse("RRULE:freq=weekly; byday=mo, fr"),
        )
    }

    @Test
    fun `sin regla, o con una que la app no entiende, se trata como diaria`() {
        assertEquals(Recurrence.Daily, Recurrence.parse(null))
        assertEquals(Recurrence.Daily, Recurrence.parse("  "))
        assertEquals(Recurrence.Daily, Recurrence.parse("FREQ=MONTHLY;BYMONTHDAY=1"))
        assertEquals(Recurrence.Daily, Recurrence.parse("cualquier cosa"))
        assertEquals(Recurrence.Daily, Recurrence.parse("FREQ=WEEKLY"))
        assertEquals(Recurrence.Daily, Recurrence.parse("FREQ=WEEKLY;BYDAY=XX"))
    }

    @Test
    fun `sin dias o con los siete, algunos dias equivale a todos`() {
        assertEquals(Recurrence.Daily, Recurrence.of(emptySet()))
        assertEquals(Recurrence.Daily, Recurrence.of(DayOfWeek.entries.toSet()))
        assertEquals(Recurrence.Weekly(setOf(DayOfWeek.SUNDAY)), Recurrence.of(setOf(DayOfWeek.SUNDAY)))
    }

    @Test
    fun `la proxima ocurrencia incluye el propio dia si toca`() {
        val mondays = Recurrence.Weekly(setOf(DayOfWeek.MONDAY))

        assertEquals(monday, mondays.nextOccurrence(saturday))
        assertEquals(monday, mondays.nextOccurrence(monday))
        assertEquals(monday.plusWeeks(1), mondays.nextOccurrence(monday.plusDays(1)))
        assertEquals(saturday, Recurrence.Daily.nextOccurrence(saturday))
    }
}
