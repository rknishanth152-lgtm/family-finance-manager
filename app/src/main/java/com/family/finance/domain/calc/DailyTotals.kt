package com.family.finance.domain.calc

import com.family.finance.domain.model.Money
import com.family.finance.domain.model.Transaction
import java.time.LocalDate
import java.time.YearMonth

/** One calendar day's activity (PRD 6.8). Transfers mark activity but never count as income or expense. */
data class DayTotal(
    val income: Money,
    val expense: Money,
    val transactionCount: Int,
) {
    val net: Money get() = income - expense
}

object DailyTotals {
    /** Days of [month] that have at least one transaction, keyed by the transaction's own date. */
    fun forMonth(transactions: Iterable<Transaction>, month: YearMonth): Map<LocalDate, DayTotal> {
        val range = DateRange(month.atDay(1), month.atEndOfMonth())
        return transactions.inRange(range)
            .groupBy { it.date }
            .mapValues { (_, dayTxns) ->
                DayTotal(
                    income = dayTxns.filterIsInstance<Transaction.Income>().fold(Money.ZERO) { a, t -> a + t.amount },
                    expense = dayTxns.filterIsInstance<Transaction.Expense>().fold(Money.ZERO) { a, t -> a + t.amount },
                    transactionCount = dayTxns.size,
                )
            }
    }
}
