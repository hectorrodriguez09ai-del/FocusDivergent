package com.focusdivergent.app.domain.tasks

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Cada cuánto se repite una rutina. Se guarda como regla RRULE (RFC 5545) en `Task.recurrenceRule`, pero la app
 * solo entiende las dos formas que ofrece su editor: todos los días o ciertos días de la semana.
 */
sealed interface Recurrence {

    fun occursOn(date: LocalDate): Boolean

    /** La regla RRULE que representa esta repetición. */
    fun toRule(): String

    /** El primer día en que toca, empezando a mirar en [from] (incluido). */
    fun nextOccurrence(from: LocalDate): LocalDate =
        generateSequence(from) { it.plusDays(1) }.first(::occursOn)

    data object Daily : Recurrence {
        override fun occursOn(date: LocalDate) = true
        override fun toRule() = "FREQ=DAILY"
    }

    /** @property days días de la semana en que toca; nunca vacío (usar [of] para construirlo con seguridad). */
    data class Weekly(val days: Set<DayOfWeek>) : Recurrence {
        init {
            require(days.isNotEmpty()) { "Una repetición semanal necesita al menos un día" }
        }

        override fun occursOn(date: LocalDate) = date.dayOfWeek in days

        override fun toRule() = "FREQ=WEEKLY;BYDAY=" + days.sorted().joinToString(",") { CODES.getValue(it) }
    }

    companion object {
        private val CODES = mapOf(
            DayOfWeek.MONDAY to "MO",
            DayOfWeek.TUESDAY to "TU",
            DayOfWeek.WEDNESDAY to "WE",
            DayOfWeek.THURSDAY to "TH",
            DayOfWeek.FRIDAY to "FR",
            DayOfWeek.SATURDAY to "SA",
            DayOfWeek.SUNDAY to "SU",
        )
        private val DAYS = CODES.entries.associate { (day, code) -> code to day }

        /** Semanal en [days]; sin ningún día o con los siete equivale a diaria. */
        fun of(days: Set<DayOfWeek>): Recurrence =
            if (days.isEmpty() || days.size == DayOfWeek.entries.size) Daily else Weekly(days)

        /**
         * Interpreta una regla guardada. Una rutina sin regla, o con una que la app no entiende, se trata como
         * diaria: es preferible que aparezca de más a que desaparezca de la lista.
         */
        fun parse(rule: String?): Recurrence {
            if (rule.isNullOrBlank()) return Daily
            val parts = rule.trim().removePrefix("RRULE:").split(';')
                .mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 } }
                .associate { (key, value) -> key.trim().uppercase() to value.trim().uppercase() }
            if (parts["FREQ"] != "WEEKLY") return Daily
            val days = parts["BYDAY"].orEmpty().split(',').mapNotNull { DAYS[it.trim()] }.toSet()
            return of(days)
        }
    }
}
