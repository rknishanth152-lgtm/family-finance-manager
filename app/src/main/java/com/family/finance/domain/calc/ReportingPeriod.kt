package com.family.finance.domain.calc

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** A dashboard period with previous/next navigation (PRD 6.1). Weeks start on Monday. */
sealed interface ReportingPeriod {
    val range: DateRange
    fun previous(): ReportingPeriod
    fun next(): ReportingPeriod

    data class Day(val date: LocalDate) : ReportingPeriod {
        override val range get() = DateRange(date, date)
        override fun previous() = Day(date.minusDays(1))
        override fun next() = Day(date.plusDays(1))
    }

    data class Week(val start: LocalDate) : ReportingPeriod {
        init {
            require(start.dayOfWeek == DayOfWeek.MONDAY) { "Week must start on Monday" }
        }
        override val range get() = DateRange(start, start.plusDays(6))
        override fun previous() = Week(start.minusWeeks(1))
        override fun next() = Week(start.plusWeeks(1))
    }

    data class Month(val month: YearMonth) : ReportingPeriod {
        override val range get() = DateRange(month.atDay(1), month.atEndOfMonth())
        override fun previous() = Month(month.minusMonths(1))
        override fun next() = Month(month.plusMonths(1))
    }

    data class Year(val year: Int) : ReportingPeriod {
        override val range get() = DateRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
        override fun previous() = Year(year - 1)
        override fun next() = Year(year + 1)
    }

    /** Previous/next shift by the custom range's own length. */
    data class Custom(override val range: DateRange) : ReportingPeriod {
        private val days get() = ChronoUnit.DAYS.between(range.start, range.end) + 1
        override fun previous() = Custom(DateRange(range.start.minusDays(days), range.end.minusDays(days)))
        override fun next() = Custom(DateRange(range.start.plusDays(days), range.end.plusDays(days)))
    }

    companion object {
        fun today(today: LocalDate): ReportingPeriod = Day(today)
        fun thisWeek(today: LocalDate): ReportingPeriod =
            Week(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
        fun thisMonth(today: LocalDate): ReportingPeriod = Month(YearMonth.from(today))
        fun lastMonth(today: LocalDate): ReportingPeriod = Month(YearMonth.from(today).minusMonths(1))
        fun thisYear(today: LocalDate): ReportingPeriod = Year(today.year)
    }
}
