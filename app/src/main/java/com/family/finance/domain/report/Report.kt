package com.family.finance.domain.report

import com.family.finance.domain.calc.BalanceCalculator
import com.family.finance.domain.calc.DateRange
import com.family.finance.domain.calc.IncomeExpenseTotals
import com.family.finance.domain.model.Account
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.Category
import com.family.finance.domain.model.CategoryId
import com.family.finance.domain.model.Money
import com.family.finance.domain.model.Transaction
import com.family.finance.domain.model.sumOfMoney
import com.family.finance.domain.usecase.SortOrder
import com.family.finance.domain.usecase.TransactionQuery
import com.family.finance.domain.usecase.TypeFilter
import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** What a report covers (PRD 6.10, 12.4). Every field except [range] is optional. */
data class ReportFilter(
    val range: DateRange,
    val accountId: AccountId? = null,
    val categoryId: CategoryId? = null,
    val type: TypeFilter = TypeFilter.ALL,
) {
    /**
     * Opening and closing balances only add up with income and expense when the report
     * covers every kind of transaction, so they are shown only then.
     */
    val showsBalances: Boolean get() = categoryId == null && type == TypeFilter.ALL

    fun select(transactions: Iterable<Transaction>): List<Transaction> =
        TransactionQuery(type = type, range = range, accountId = accountId, categoryId = categoryId, sort = SortOrder.DATE_ASC)
            .apply(transactions)
}

enum class Bucket { DAY, MONTH, YEAR }

/** One bar of the income-versus-expense chart and one row of the daily/monthly/annual summary. */
data class BucketTotal(val range: DateRange, val totals: IncomeExpenseTotals)

data class CategoryTotal(val categoryId: CategoryId, val total: Money, val count: Int, val isLoan: Boolean)

/** Income grouped by the "received from" text exactly as typed; null means not given. */
data class SourceTotal(val source: String?, val total: Money, val count: Int)

/**
 * One account over the period (PRD 6.5):
 * [closing] = [opening] + [openingAdded] + [income] - [expense] + [transfersIn] - [transfersOut].
 */
data class AccountMovement(
    val account: Account,
    val opening: Money,
    val openingAdded: Money,
    val income: Money,
    val expense: Money,
    val transfersIn: Money,
    val transfersOut: Money,
    val closing: Money,
)

/** Opening/closing of the accounts in scope, with the parts that explain the difference. */
data class Balances(
    val opening: Money,
    val openingAdded: Money,
    val transfersIn: Money,
    val transfersOut: Money,
    val closing: Money,
)

/**
 * Every figure of the Reports screen and the exported statements (PRD 6.10, 12.1), all
 * derived from the same transaction records as the history screen; nothing is stored.
 * Transfers never count as income or expense (PRD 9.2).
 */
data class Report(
    val filter: ReportFilter,
    /** The selected transactions, oldest first: the transaction history report. */
    val transactions: List<Transaction>,
    val totals: IncomeExpenseTotals,
    /** Null when a category or type filter makes balances meaningless (see [ReportFilter.showsBalances]). */
    val balances: Balances?,
    /** Loan money received and principal repaid, already included in [totals] (PRD 6.2, 6.3). */
    val loanReceived: Money,
    val loanRepaid: Money,
    val expenseByCategory: List<CategoryTotal>,
    val incomeByCategory: List<CategoryTotal>,
    val incomeBySource: List<SourceTotal>,
    val accounts: List<AccountMovement>,
) {
    fun buckets(bucket: Bucket): List<BucketTotal> = Reports.buckets(transactions, filter.range, bucket)

    val transferTotal: Money get() = transactions.filterIsInstance<Transaction.Transfer>().sumOfMoney { it.amount }
}

object Reports {

    fun build(
        filter: ReportFilter,
        allTransactions: List<Transaction>,
        accounts: List<Account>,
        categories: List<Category>,
    ): Report {
        val selected = filter.select(allTransactions)
        val loanCategories = categories.filter { it.isLoan }.map { it.id }.toSet()
        val inScope = accounts.filter { filter.accountId == null || it.id == filter.accountId }
        val movements = inScope.map { movement(it, allTransactions, filter.range) }

        val incomes = selected.filterIsInstance<Transaction.Income>()
        val expenses = selected.filterIsInstance<Transaction.Expense>()

        return Report(
            filter = filter,
            transactions = selected,
            totals = IncomeExpenseTotals.of(selected),
            balances = if (filter.showsBalances) balances(movements, filter.accountId == null) else null,
            loanReceived = incomes.filter { it.categoryId in loanCategories }.sumOfMoney { it.amount },
            loanRepaid = expenses.filter { it.categoryId in loanCategories }.sumOfMoney { it.amount },
            expenseByCategory = byCategory(expenses.map { it.categoryId to it.amount }, loanCategories),
            incomeByCategory = byCategory(incomes.map { it.categoryId to it.amount }, loanCategories),
            incomeBySource = bySource(incomes),
            accounts = movements,
        )
    }

    /**
     * Transfers between two accounts that are both in scope cancel out, so for the whole
     * family only transfers to or from accounts outside it would show; with every account
     * in scope they are always zero and are left out.
     */
    private fun balances(movements: List<AccountMovement>, allAccounts: Boolean) = Balances(
        opening = movements.sumOfMoney { it.opening },
        openingAdded = movements.sumOfMoney { it.openingAdded },
        transfersIn = if (allAccounts) Money.ZERO else movements.sumOfMoney { it.transfersIn },
        transfersOut = if (allAccounts) Money.ZERO else movements.sumOfMoney { it.transfersOut },
        closing = movements.sumOfMoney { it.closing },
    )

    fun movement(account: Account, transactions: List<Transaction>, range: DateRange): AccountMovement {
        val inPeriod = transactions.filter { it.date in range }
        var income = Money.ZERO
        var expense = Money.ZERO
        var transfersIn = Money.ZERO
        var transfersOut = Money.ZERO
        for (t in inPeriod) {
            when (t) {
                is Transaction.Income -> if (t.accountId == account.id) income += t.amount
                is Transaction.Expense -> if (t.accountId == account.id) expense += t.amount
                is Transaction.Transfer -> {
                    if (t.toAccountId == account.id) transfersIn += t.amount
                    if (t.fromAccountId == account.id) transfersOut += t.amount
                }
            }
        }
        return AccountMovement(
            account = account,
            opening = BalanceCalculator.accountBalance(account, transactions, range.start.minusDays(1)),
            openingAdded = if (account.openingBalanceDate in range) account.openingBalance else Money.ZERO,
            income = income,
            expense = expense,
            transfersIn = transfersIn,
            transfersOut = transfersOut,
            closing = BalanceCalculator.accountBalance(account, transactions, range.end),
        )
    }

    /** Largest first; ties keep a stable order by category ID. */
    private fun byCategory(items: List<Pair<CategoryId, Money>>, loans: Set<CategoryId>): List<CategoryTotal> =
        items.groupBy({ it.first }, { it.second })
            .map { (id, amounts) -> CategoryTotal(id, amounts.sumOfMoney { it }, amounts.size, id in loans) }
            .sortedWith(compareByDescending<CategoryTotal> { it.total }.thenBy { it.categoryId.value })

    /**
     * Groups by the source text with outer and repeated spaces removed, so "Ravi " and
     * "Ravi" are one source; the text itself (Tamil or English) is kept as typed.
     */
    private fun bySource(incomes: List<Transaction.Income>): List<SourceTotal> =
        incomes.groupBy { it.source.trim().replace(WHITESPACE, " ").ifEmpty { null } }
            .map { (source, items) -> SourceTotal(source, items.sumOfMoney { it.amount }, items.size) }
            .sortedWith(compareByDescending<SourceTotal> { it.total }.thenBy { it.source.orEmpty() })

    /**
     * Every day, month or year the range touches, including empty ones, so a chart shows
     * gaps honestly. The first and last buckets are clipped to the range.
     */
    fun buckets(transactions: List<Transaction>, range: DateRange, bucket: Bucket): List<BucketTotal> {
        val byStart = transactions.filter { it.date in range }.groupBy { startOf(it.date, bucket) }
        return generateSequence(startOf(range.start, bucket)) { next(it, bucket) }
            .takeWhile { !it.isAfter(range.end) }
            .map { start ->
                val clipped = DateRange(maxOf(start, range.start), minOf(endOf(start, bucket), range.end))
                BucketTotal(clipped, IncomeExpenseTotals.of(byStart[start].orEmpty()))
            }
            .toList()
    }

    /** Days for up to a month, months for up to two years, years beyond that. */
    fun defaultBucket(range: DateRange): Bucket {
        val days = ChronoUnit.DAYS.between(range.start, range.end) + 1
        val months = ChronoUnit.MONTHS.between(YearMonth.from(range.start), YearMonth.from(range.end)) + 1
        return when {
            days <= 31 -> Bucket.DAY
            months <= 24 -> Bucket.MONTH
            else -> Bucket.YEAR
        }
    }

    /**
     * [part] as a share of [whole] in hundredths of a percent (1250 = 12.50%), rounded half
     * up. Exact integer arithmetic, so the shown percentages never drift from the amounts.
     */
    fun shareBasisPoints(part: Money, whole: Money): Long {
        if (!whole.isPositive) return 0
        // Widened so very large family totals can never overflow while computing a percentage.
        val scaled = BigInteger.valueOf(part.paise).multiply(BigInteger.valueOf(10_000L))
        val whole2 = BigInteger.valueOf(whole.paise)
        return scaled.add(whole2.shiftRight(1)).divide(whole2).toLong()
    }

    private fun startOf(date: LocalDate, bucket: Bucket): LocalDate = when (bucket) {
        Bucket.DAY -> date
        Bucket.MONTH -> date.withDayOfMonth(1)
        Bucket.YEAR -> date.withDayOfYear(1)
    }

    private fun next(start: LocalDate, bucket: Bucket): LocalDate = when (bucket) {
        Bucket.DAY -> start.plusDays(1)
        Bucket.MONTH -> start.plusMonths(1)
        Bucket.YEAR -> start.plusYears(1)
    }

    private fun endOf(start: LocalDate, bucket: Bucket): LocalDate = next(start, bucket).minusDays(1)

    private val WHITESPACE = Regex("\\s+")
}
