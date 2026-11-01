package com.family.finance.domain.model

import java.time.Instant
import java.time.LocalDate

enum class AccountType { CASH, SAVINGS, CURRENT, DIGITAL_WALLET, OTHER }

/**
 * A tracked account (PRD 6.5). Its current balance is never stored; it is derived from
 * [openingBalance] plus transactions by [com.family.finance.domain.calc.BalanceCalculator].
 */
data class Account(
    val id: AccountId,
    val name: String,
    val type: AccountType,
    val openingBalance: Money,
    /** The opening balance is the account's balance at the start of this date. */
    val openingBalanceDate: LocalDate,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val currencyCode: String = INR,
) {
    companion object {
        const val INR = "INR"
    }
}
