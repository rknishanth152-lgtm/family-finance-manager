package com.family.finance.domain.calc

import com.family.finance.domain.Fixtures.BANK_ID
import com.family.finance.domain.Fixtures.CASH_ID
import com.family.finance.domain.Fixtures.account
import com.family.finance.domain.Fixtures.date
import com.family.finance.domain.Fixtures.expense
import com.family.finance.domain.Fixtures.income
import com.family.finance.domain.Fixtures.rupees
import com.family.finance.domain.Fixtures.transfer
import com.family.finance.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodSummaryTest {

    private val accounts = listOf(
        account(CASH_ID, opening = rupees(10_000)),
        account(BANK_ID, opening = rupees(40_000)),
    )
    private val march = DateRange(date("2026-03-01"), date("2026-03-31"))

    @Test fun `totals and net cash flow`() {
        val txns = listOf(
            income(rupees(30_000), BANK_ID, on = "2026-03-05"),
            expense(rupees(4_000), CASH_ID, on = "2026-03-06"),
            expense(rupees(1_000), BANK_ID, on = "2026-03-20"),
        )
        val s = PeriodSummary.calculate(accounts, txns, march)
        assertEquals(rupees(30_000), s.totalIncome)
        assertEquals(rupees(5_000), s.totalExpense)
        assertEquals(rupees(25_000), s.netCashFlow)
    }

    @Test fun `transfers are excluded from income and expense totals`() {
        val txns = listOf(
            transfer(rupees(20_000), from = CASH_ID, to = BANK_ID, on = "2026-03-10"),
            transfer(rupees(5_000), from = BANK_ID, to = CASH_ID, on = "2026-03-11"),
        )
        val s = PeriodSummary.calculate(accounts, txns, march)
        assertEquals(Money.ZERO, s.totalIncome)
        assertEquals(Money.ZERO, s.totalExpense)
        assertEquals(Money.ZERO, s.netCashFlow)
        assertEquals(s.openingBalance, s.closingBalance)
    }

    @Test fun `opening and closing balance across period boundaries`() {
        val txns = listOf(
            income(rupees(2_000), CASH_ID, on = "2026-02-28"), // before: in opening balance
            expense(rupees(500), CASH_ID, on = "2026-03-01"),  // first day: in period
            income(rupees(8_000), BANK_ID, on = "2026-03-31"), // last day: in period
            expense(rupees(9_999), BANK_ID, on = "2026-04-01"), // after: ignored
        )
        val s = PeriodSummary.calculate(accounts, txns, march)
        assertEquals(rupees(52_000), s.openingBalance)
        assertEquals(rupees(8_000), s.totalIncome)
        assertEquals(rupees(500), s.totalExpense)
        assertEquals(rupees(59_500), s.closingBalance)
        assertEquals(s.openingBalance + s.netCashFlow, s.closingBalance)
    }

    @Test fun `closing of one month equals opening of the next`() {
        val txns = listOf(
            income(rupees(3_000), CASH_ID, on = "2026-03-15"),
            expense(rupees(700), BANK_ID, on = "2026-04-02"),
        )
        val april = DateRange(date("2026-04-01"), date("2026-04-30"))
        assertEquals(
            PeriodSummary.calculate(accounts, txns, march).closingBalance,
            PeriodSummary.calculate(accounts, txns, april).openingBalance,
        )
    }

    @Test fun `account opened mid-period is reported as an opening-balance addition`() {
        val withWallet = accounts + account(
            com.family.finance.domain.model.AccountId("wallet"),
            opening = rupees(1_500),
            openingDate = date("2026-03-15"),
        )
        val s = PeriodSummary.calculate(withWallet, emptyList(), march)
        assertEquals(rupees(50_000), s.openingBalance)
        assertEquals(rupees(1_500), s.openingBalancesAdded)
        assertEquals(s.openingBalance + s.netCashFlow + s.openingBalancesAdded, s.closingBalance)
    }

    @Test fun `date range filter includes start and end dates`() {
        val txns = listOf(
            income(rupees(1), CASH_ID, on = "2026-02-28"),
            income(rupees(2), CASH_ID, on = "2026-03-01"),
            income(rupees(3), CASH_ID, on = "2026-03-31"),
            income(rupees(4), CASH_ID, on = "2026-04-01"),
        )
        val filtered = txns.inRange(march)
        assertEquals(listOf(rupees(2), rupees(3)), filtered.map { it.amount })
        assertTrue(date("2026-03-01") in march)
        assertTrue(date("2026-03-31") in march)
        assertFalse(date("2026-02-28") in march)
        assertFalse(date("2026-04-01") in march)
    }

    @Test fun `single-day range includes that day`() {
        val day = DateRange(date("2026-03-15"), date("2026-03-15"))
        val s = PeriodSummary.calculate(accounts, listOf(income(rupees(100), CASH_ID, on = "2026-03-15")), day)
        assertEquals(rupees(100), s.totalIncome)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `range with end before start is rejected`() {
        DateRange(date("2026-03-31"), date("2026-03-01"))
    }
}
