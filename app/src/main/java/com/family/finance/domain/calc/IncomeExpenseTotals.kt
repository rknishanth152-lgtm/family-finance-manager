package com.family.finance.domain.calc

import com.family.finance.domain.model.Money
import com.family.finance.domain.model.Transaction

/**
 * The "Income | Expense | Balance" figures for any list of transactions, where
 * balance = income - expense. Transfers are ignored: they move money between
 * accounts and are neither income nor expense (PRD 9.2).
 */
data class IncomeExpenseTotals(val income: Money, val expense: Money) {
    val balance: Money get() = income - expense

    companion object {
        val ZERO = IncomeExpenseTotals(Money.ZERO, Money.ZERO)

        fun of(transactions: Iterable<Transaction>): IncomeExpenseTotals {
            var income = Money.ZERO
            var expense = Money.ZERO
            for (t in transactions) {
                when (t) {
                    is Transaction.Income -> income += t.amount
                    is Transaction.Expense -> expense += t.amount
                    is Transaction.Transfer -> Unit
                }
            }
            return IncomeExpenseTotals(income, expense)
        }
    }
}
