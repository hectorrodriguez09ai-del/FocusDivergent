package com.focusdivergent.app.presentation.common

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

// Fechas y horas tal como se leen en la interfaz, en el idioma y formato del móvil.

/** «14:30» o «2:30 p. m.», según la configuración regional. */
fun LocalTime.formatShort(locale: Locale = Locale.getDefault()): String =
    format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))

/** «vie 9 oct»: para listas, donde el espacio es poco. */
fun LocalDate.formatShort(locale: Locale = Locale.getDefault()): String =
    format(DateTimeFormatter.ofPattern("EEE d MMM", locale))

/** «viernes, 9 de octubre de 2026»: para títulos y lectores de pantalla. */
fun LocalDate.formatFull(locale: Locale = Locale.getDefault()): String =
    format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))

/** «octubre de 2026», con la inicial en mayúscula. */
fun LocalDate.formatMonth(locale: Locale = Locale.getDefault()): String =
    format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).replaceFirstChar { it.titlecase(locale) }

/** Inicial del día de la semana («L», «M», «X»…). */
fun LocalDate.weekdayInitial(locale: Locale = Locale.getDefault()): String =
    dayOfWeek.getDisplayName(TextStyle.NARROW, locale).uppercase(locale)
