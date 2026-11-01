package com.family.finance.domain.calc

import com.family.finance.domain.Fixtures.BANK_ID
import com.family.finance.domain.Fixtures.CASH_ID
import com.family.finance.domain.Fixtures.date
import com.family.finance.domain.Fixtures.expense
import com.family.finance.domain.Fixtures.income
import com.family.finance.domain.Fixtures.rupees
import com.family.finance.domain.Fixtures.transfer
import com.family.finance.domain.model.Money
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.YearMonth

class PeriodAndCalendarTest {

    @Test fun `this week starts on Monday`() {
        // 2026-03-15 is a Sunday.
        val week = ReportingPeriod.thisWeek(date("2026-03-15"))
        assertThat(week.range).isEqualTo(DateRange(date("2026-03-09"), date("2026-03-15")))
    }

    @Test fun `month periods handle leap years and step correctly`() {
        val feb = ReportingPeriod.Month(YearMonth.of(2028, 2))
        assertThat(feb.range.end).isEqualTo(date("2028-02-29"))
        assertThat(feb.next()).isEqualTo(ReportingPeriod.Month(YearMonth.of(2028, 3)))
        assertThat(ReportingPeriod.lastMonth(date("2026-01-10"))).isEqualTo(ReportingPeriod.Month(YearMonth.of(2025, 12)))
    }

    @Test fun `day, year and custom periods step by their own length`() {
        assertThat(ReportingPeriod.today(date("2026-03-01")).previous()).isEqualTo(ReportingPeriod.Day(date("2026-02-28")))
        assertThat(ReportingPeriod.thisYear(date("2026-06-01")).range)
            .isEqualTo(DateRange(date("2026-01-01"), date("2026-12-31")))
        val custom = ReportingPeriod.Custom(DateRange(date("2026-03-01"), date("2026-03-10")))
        assertThat(custom.next().range).isEqualTo(DateRange(date("2026-03-11"), date("2026-03-20")))
    }

    @Test fun `daily totals add income and expense per day and ignore transfers`() {
        val txns = listOf(
            income(rupees(500), CASH_ID, on = "2026-03-05"),
            expense(rupees(200), CASH_ID, on = "2026-03-05"),
            expense(rupees(50), BANK_ID, on = "2026-03-05"),
            transfer(rupees(9_000), from = CASH_ID, to = BANK_ID, on = "2026-03-06"),
            income(rupees(1), CASH_ID, on = "2026-04-01"),
        )
        val totals = DailyTotals.forMonth(txns, YearMonth.of(2026, 3))

        assertThat(totals.keys).containsExactly(date("2026-03-05"), date("2026-03-06"))
        val fifth = totals.getValue(date("2026-03-05"))
        assertThat(fifth.income).isEqualTo(rupees(500))
        assertThat(fifth.expense).isEqualTo(rupees(250))
        assertThat(fifth.net).isEqualTo(rupees(250))
        assertThat(fifth.transactionCount).isEqualTo(3)
        val sixth = totals.getValue(date("2026-03-06"))
        assertThat(sixth.income).isEqualTo(Money.ZERO)
        assertThat(sixth.expense).isEqualTo(Money.ZERO)
        assertThat(sixth.transactionCount).isEqualTo(1)
    }
}
