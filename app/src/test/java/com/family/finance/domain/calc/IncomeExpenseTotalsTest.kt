package com.family.finance.domain.calc

import com.family.finance.domain.Fixtures.BANK_ID
import com.family.finance.domain.Fixtures.CASH_ID
import com.family.finance.domain.Fixtures.expense
import com.family.finance.domain.Fixtures.income
import com.family.finance.domain.Fixtures.rupees
import com.family.finance.domain.Fixtures.transfer
import com.family.finance.domain.model.Money
import com.family.finance.domain.usecase.TransactionQuery
import com.family.finance.domain.usecase.TypeFilter
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IncomeExpenseTotalsTest {

    private val txns = listOf(
        income(rupees(45_000), BANK_ID),
        expense(rupees(32_150), CASH_ID),
        expense(Money(5), CASH_ID),
        transfer(rupees(20_000), from = CASH_ID, to = BANK_ID),
    )

    @Test fun `balance is income minus expense and ignores transfers`() {
        val totals = IncomeExpenseTotals.of(txns)
        assertThat(totals.income).isEqualTo(rupees(45_000))
        assertThat(totals.expense).isEqualTo(Money(3_215_005))
        assertThat(totals.balance).isEqualTo(Money(1_284_995))
    }

    @Test fun `balance can go negative when spending exceeds income`() {
        val totals = IncomeExpenseTotals.of(listOf(income(rupees(100), CASH_ID), expense(rupees(250), CASH_ID)))
        assertThat(totals.balance).isEqualTo(-rupees(150))
    }

    @Test fun `empty and transfer-only lists balance to zero`() {
        assertThat(IncomeExpenseTotals.of(emptyList())).isEqualTo(IncomeExpenseTotals.ZERO)
        assertThat(IncomeExpenseTotals.of(listOf(txns.last())).balance).isEqualTo(Money.ZERO)
    }

    @Test fun `totals follow whatever filter is applied`() {
        val expensesOnly = TransactionQuery(type = TypeFilter.EXPENSE).apply(txns)
        val totals = IncomeExpenseTotals.of(expensesOnly)
        assertThat(totals.income).isEqualTo(Money.ZERO)
        assertThat(totals.balance).isEqualTo(-Money(3_215_005))
    }

    @Test fun `period summary agrees with the strip`() {
        val accounts = listOf(com.family.finance.domain.Fixtures.account(CASH_ID), com.family.finance.domain.Fixtures.account(BANK_ID))
        val range = DateRange(java.time.LocalDate.parse("2026-03-01"), java.time.LocalDate.parse("2026-03-31"))
        val summary = PeriodSummary.calculate(accounts, txns, range)
        val strip = IncomeExpenseTotals.of(txns.inRange(range))
        assertThat(strip.income).isEqualTo(summary.totalIncome)
        assertThat(strip.expense).isEqualTo(summary.totalExpense)
        assertThat(strip.balance).isEqualTo(summary.netCashFlow)
    }
}
