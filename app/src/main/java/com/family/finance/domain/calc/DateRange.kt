package com.family.finance.domain.calc

import com.family.finance.domain.model.Transaction
import java.time.LocalDate

/** A reporting period. Both [start] and [end] are included. */
data class DateRange(val start: LocalDate, val end: LocalDate) {
    init {
        require(!end.isBefore(start)) { "end ($end) is before start ($start)" }
    }

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
}

fun Iterable<Transaction>.inRange(range: DateRange): List<Transaction> = filter { it.date in range }
