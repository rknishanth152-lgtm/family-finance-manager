package com.family.finance.domain.report

import com.family.finance.domain.Fixtures.BANK_ID
import com.family.finance.domain.Fixtures.CASH_ID
import com.family.finance.domain.Fixtures.GROCERIES_ID
import com.family.finance.domain.Fixtures.SALARY_ID
import com.family.finance.domain.Fixtures.account
import com.family.finance.domain.Fixtures.category
import com.family.finance.domain.Fixtures.date
import com.family.finance.domain.Fixtures.expense
import com.family.finance.domain.Fixtures.income
import com.family.finance.domain.Fixtures.rupees
import com.family.finance.domain.Fixtures.transfer
import com.family.finance.domain.calc.DateRange
import com.family.finance.domain.calc.IncomeExpenseTotals
import com.family.finance.domain.calc.PeriodSummary
import com.family.finance.domain.model.CategoryId
import com.family.finance.domain.model.CategoryKind
import com.family.finance.domain.model.Money
import com.family.finance.domain.model.Transaction
import com.family.finance.domain.usecase.TypeFilter
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReportsTest {

    private val loanId = CategoryId("loan_received")
    private val repayId = CategoryId("loan_principal")
    private val seedsId = CategoryId("seeds")
    private val categories = listOf(
        category(SALARY_ID, CategoryKind.INCOME),
        category(GROCERIES_ID, CategoryKind.EXPENSE),
        category(seedsId, CategoryKind.EXPENSE),
        category(loanId, CategoryKind.INCOME).copy(isLoan = true),
        category(repayId, CategoryKind.EXPENSE).copy(isLoan = true),
    )
    private val accounts = listOf(
        account(CASH_ID, opening = rupees(10_000)),
        account(BANK_ID, opening = rupees(40_000)),
    )
    private val march = DateRange(date("2026-03-01"), date("2026-03-31"))

    private fun report(txns: List<Transaction>, filter: ReportFilter = ReportFilter(march)) =
        Reports.build(filter, txns, accounts, categories)

    private fun sample() = listOf(
        income(rupees(30_000), BANK_ID, on = "2026-03-05"),
        income(rupees(50_000), BANK_ID, on = "2026-03-06").copy(categoryId = loanId, source = "கூட்டுறவு வங்கி"),
        expense(rupees(4_000), CASH_ID, on = "2026-03-06"),
        expense(rupees(2_500), CASH_ID, on = "2026-03-20").copy(categoryId = seedsId),
        expense(rupees(10_000), BANK_ID, on = "2026-03-25").copy(categoryId = repayId),
        transfer(rupees(5_000), from = BANK_ID, to = CASH_ID, on = "2026-03-10"),
        // Outside the period: affects the opening balance only.
        income(rupees(1_000), CASH_ID, on = "2026-02-27"),
        expense(rupees(700), CASH_ID, on = "2026-04-01"),
    )

    @Test fun `totals match the Home summary for the same period`() {
        val txns = sample()
        val r = report(txns)
        val home = PeriodSummary.calculate(accounts, txns, march)
        assertThat(r.totals.income).isEqualTo(home.totalIncome)
        assertThat(r.totals.expense).isEqualTo(home.totalExpense)
        assertThat(r.balances!!.opening).isEqualTo(home.openingBalance)
        assertThat(r.balances!!.closing).isEqualTo(home.closingBalance)
    }

    @Test fun `transfers are never income or expense`() {
        val r = report(listOf(transfer(rupees(5_000), from = BANK_ID, to = CASH_ID, on = "2026-03-10")))
        assertThat(r.totals).isEqualTo(IncomeExpenseTotals.ZERO)
        assertThat(r.transferTotal).isEqualTo(rupees(5_000))
        assertThat(r.expenseByCategory).isEmpty()
        assertThat(r.balances!!.closing).isEqualTo(r.balances!!.opening)
    }

    @Test fun `closing equals opening plus income minus expense for all accounts`() {
        val r = report(sample())
        val b = r.balances!!
        assertThat(b.closing).isEqualTo(b.opening + b.openingAdded + r.totals.income - r.totals.expense)
        assertThat(b.transfersIn).isEqualTo(Money.ZERO)
        assertThat(b.transfersOut).isEqualTo(Money.ZERO)
    }

    @Test fun `one account report includes its transfers in the balance`() {
        val r = report(sample(), ReportFilter(march, accountId = CASH_ID))
        val b = r.balances!!
        assertThat(b.opening).isEqualTo(rupees(11_000))
        assertThat(b.transfersIn).isEqualTo(rupees(5_000))
        assertThat(r.totals.expense).isEqualTo(rupees(6_500))
        assertThat(b.closing).isEqualTo(b.opening + r.totals.income - r.totals.expense + b.transfersIn - b.transfersOut)
        assertThat(b.closing).isEqualTo(rupees(9_500))
    }

    @Test fun `every account movement adds up`() {
        val r = report(sample())
        assertThat(r.accounts).hasSize(2)
        for (m in r.accounts) {
            assertThat(m.closing).isEqualTo(m.opening + m.openingAdded + m.income - m.expense + m.transfersIn - m.transfersOut)
        }
        assertThat(r.accounts.map { it.closing }.reduce(Money::plus)).isEqualTo(r.balances!!.closing)
    }

    @Test fun `an account opened inside the period is shown as an opening balance added`() {
        val late = account(CASH_ID, opening = rupees(2_000), openingDate = date("2026-03-15"))
        val r = Reports.build(ReportFilter(march), emptyList(), listOf(late), categories)
        assertThat(r.balances!!.opening).isEqualTo(Money.ZERO)
        assertThat(r.balances!!.openingAdded).isEqualTo(rupees(2_000))
        assertThat(r.balances!!.closing).isEqualTo(rupees(2_000))
    }

    @Test fun `category or type filters hide balances that would not add up`() {
        assertThat(report(sample(), ReportFilter(march, categoryId = seedsId)).balances).isNull()
        assertThat(report(sample(), ReportFilter(march, type = TypeFilter.EXPENSE)).balances).isNull()
        assertThat(report(sample(), ReportFilter(march, accountId = BANK_ID)).balances).isNotNull()
    }

    @Test fun `category filter keeps only that category`() {
        val r = report(sample(), ReportFilter(march, categoryId = seedsId))
        assertThat(r.transactions).hasSize(1)
        assertThat(r.totals).isEqualTo(IncomeExpenseTotals(Money.ZERO, rupees(2_500)))
    }

    @Test fun `type filter keeps only that type`() {
        val r = report(sample(), ReportFilter(march, type = TypeFilter.TRANSFER))
        assertThat(r.transactions.map { it::class }).containsExactly(Transaction.Transfer::class)
        assertThat(r.totals).isEqualTo(IncomeExpenseTotals.ZERO)
    }

    @Test fun `loans are counted in the totals and also shown apart`() {
        val r = report(sample())
        assertThat(r.loanReceived).isEqualTo(rupees(50_000))
        assertThat(r.loanRepaid).isEqualTo(rupees(10_000))
        assertThat(r.totals.income).isEqualTo(rupees(80_000))
        assertThat(r.incomeByCategory.single { it.categoryId == loanId }.isLoan).isTrue()
    }

    @Test fun `categories are largest first and add up to the total`() {
        val r = report(sample())
        assertThat(r.expenseByCategory.map { it.categoryId }).containsExactly(repayId, GROCERIES_ID, seedsId).inOrder()
        assertThat(r.expenseByCategory.map { it.total }.reduce(Money::plus)).isEqualTo(r.totals.expense)
        assertThat(r.incomeByCategory.map { it.total }.reduce(Money::plus)).isEqualTo(r.totals.income)
    }

    @Test fun `income by source keeps Tamil text as typed and merges extra spaces`() {
        val txns = listOf(
            income(rupees(100), CASH_ID, on = "2026-03-02").copy(source = "சுப்பிரமணி"),
            income(rupees(200), CASH_ID, on = "2026-03-03").copy(source = "  சுப்பிரமணி "),
            income(rupees(50), CASH_ID, on = "2026-03-04").copy(source = ""),
        )
        val sources = report(txns).incomeBySource
        assertThat(sources).containsExactly(
            SourceTotal("சுப்பிரமணி", rupees(300), 2),
            SourceTotal(null, rupees(50), 1),
        ).inOrder()
    }

    @Test fun `transactions are listed oldest first`() {
        val dates = report(sample()).transactions.map { it.date }
        assertThat(dates).isInOrder()
        assertThat(dates.first()).isEqualTo(date("2026-03-05"))
    }

    @Test fun `daily buckets cover every day including empty ones`() {
        val r = report(sample())
        val days = r.buckets(Bucket.DAY)
        assertThat(days).hasSize(31)
        assertThat(days.first { it.range.start == date("2026-03-06") }.totals)
            .isEqualTo(IncomeExpenseTotals(rupees(50_000), rupees(4_000)))
        assertThat(days.first { it.range.start == date("2026-03-07") }.totals).isEqualTo(IncomeExpenseTotals.ZERO)
        assertThat(days.map { it.totals.income }.reduce(Money::plus)).isEqualTo(r.totals.income)
    }

    @Test fun `monthly buckets are clipped to the range`() {
        val range = DateRange(date("2026-02-15"), date("2026-04-10"))
        val months = Reports.buckets(sample(), range, Bucket.MONTH)
        assertThat(months.map { it.range }).containsExactly(
            DateRange(date("2026-02-15"), date("2026-02-28")),
            DateRange(date("2026-03-01"), date("2026-03-31")),
            DateRange(date("2026-04-01"), date("2026-04-10")),
        ).inOrder()
        assertThat(months[0].totals.income).isEqualTo(rupees(1_000))
        assertThat(months[2].totals.expense).isEqualTo(rupees(700))
    }

    @Test fun `yearly buckets`() {
        val range = DateRange(date("2024-06-01"), date("2026-12-31"))
        assertThat(Reports.buckets(sample(), range, Bucket.YEAR).map { it.range.start.year }).containsExactly(2024, 2025, 2026).inOrder()
    }

    @Test fun `default bucket follows the range length`() {
        assertThat(Reports.defaultBucket(march)).isEqualTo(Bucket.DAY)
        assertThat(Reports.defaultBucket(DateRange(date("2026-01-01"), date("2026-12-31")))).isEqualTo(Bucket.MONTH)
        assertThat(Reports.defaultBucket(DateRange(date("2020-01-01"), date("2026-12-31")))).isEqualTo(Bucket.YEAR)
    }

    @Test fun `share is exact in hundredths of a percent`() {
        assertThat(Reports.shareBasisPoints(rupees(1), rupees(3))).isEqualTo(3333)
        assertThat(Reports.shareBasisPoints(rupees(2), rupees(3))).isEqualTo(6667)
        assertThat(Reports.shareBasisPoints(rupees(5), rupees(5))).isEqualTo(10_000)
        assertThat(Reports.shareBasisPoints(rupees(5), Money.ZERO)).isEqualTo(0)
        // Totals in crores of rupees must not overflow.
        assertThat(Reports.shareBasisPoints(rupees(348_26_190), rupees(348_26_190))).isEqualTo(10_000)
        // Even totals far above any real family's cannot overflow the percentage.
        assertThat(Reports.shareBasisPoints(Money(Long.MAX_VALUE / 2), Money(Long.MAX_VALUE))).isEqualTo(5_000)
    }

    @Test fun `paise are kept exactly`() {
        val txns = listOf(
            income(Money(10), CASH_ID, on = "2026-03-01"),
            income(Money(20), CASH_ID, on = "2026-03-01"),
            expense(Money(1), CASH_ID, on = "2026-03-02"),
        )
        val r = report(txns)
        assertThat(r.totals).isEqualTo(IncomeExpenseTotals(Money(30), Money(1)))
    }
}
