package com.family.finance.domain.calc

import com.family.finance.domain.model.Account
import com.family.finance.domain.model.Money
import com.family.finance.domain.model.Transaction
import com.family.finance.domain.model.sumOfMoney

/**
 * Dashboard and statement figures for one period (PRD 6.1).
 *
 * [closingBalance] always equals
 * [openingBalance] + [netCashFlow] + [openingBalancesAdded], where
 * [openingBalancesAdded] is the opening balance of any account whose opening-balance date
 * falls inside the period (the "opening-balance correction" the PRD allows for).
 */
data class PeriodSummary(
    val range: DateRange,
    val totalIncome: Money,
    val totalExpense: Money,
    val openingBalance: Money,
    val openingBalancesAdded: Money,
    val closingBalance: Money,
) {
    val netCashFlow: Money get() = totalIncome - totalExpense

    companion object {
        fun calculate(
            accounts: Iterable<Account>,
            transactions: Iterable<Transaction>,
            range: DateRange,
        ): PeriodSummary {
            val accountList = accounts.toList()
            val txns = transactions.toList()
            val inPeriod = txns.inRange(range)

            // Transfers are deliberately excluded from both totals (PRD 9.2).
            val income = inPeriod.filterIsInstance<Transaction.Income>().sumOfMoney { it.amount }
            val expense = inPeriod.filterIsInstance<Transaction.Expense>().sumOfMoney { it.amount }

            return PeriodSummary(
                range = range,
                totalIncome = income,
                totalExpense = expense,
                openingBalance = BalanceCalculator.combinedBalance(accountList, txns, range.start.minusDays(1)),
                openingBalancesAdded = accountList
                    .filter { it.openingBalanceDate in range }
                    .sumOfMoney { it.openingBalance },
                closingBalance = BalanceCalculator.combinedBalance(accountList, txns, range.end),
            )
        }
    }
}
