package com.focusdivergent.app.domain.time

import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/** Medianoche local que abre el día de hoy: a partir de ahí se reinician límites y ampliaciones. */
fun Clock.startOfToday(): Instant = LocalDate.now(this).atStartOfDay(zone).toInstant()

/** La próxima medianoche local: cuándo empieza mañana y entran en vigor los límites relajados. */
fun Clock.startOfTomorrow(): Instant = LocalDate.now(this).plusDays(1).atStartOfDay(zone).toInstant()
