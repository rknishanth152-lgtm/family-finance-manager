package com.family.finance.domain.calc

import com.family.finance.domain.model.Account
import com.family.finance.domain.model.Money
import com.family.finance.domain.model.Transaction
import com.family.finance.domain.model.sumOfMoney
import java.time.LocalDate

/**
 * Derives balances from opening balances and transactions (PRD 9.1):
 * opening + income - expenses + transfers in - transfers out.
 *
 * "As of" a date means at the end of that day. An account's opening balance counts from
 * its opening-balance date onwards; before that date the account contributes nothing.
 */
object BalanceCalculator {

    fun accountBalance(
        account: Account,
        transactions: Iterable<Transaction>,
        asOf: LocalDate? = null,
    ): Money {
        val opening =
            if (asOf == null || !account.openingBalanceDate.isAfter(asOf)) account.openingBalance
            else Money.ZERO
        val movements = transactions
            .filter { asOf == null || !it.date.isAfter(asOf) }
            .sumOfMoney { it.effectOn(account.id) }
        return opening + movements
    }

    /** Sum of the balances of [accounts]. Transfers between them cancel out. */
    fun combinedBalance(
        accounts: Iterable<Account>,
        transactions: Iterable<Transaction>,
        asOf: LocalDate? = null,
    ): Money {
        val txns = transactions.toList()
        return accounts.sumOfMoney { accountBalance(it, txns, asOf) }
    }
}
