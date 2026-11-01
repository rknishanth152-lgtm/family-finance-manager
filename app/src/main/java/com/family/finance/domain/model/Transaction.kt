package com.family.finance.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * A financial record (PRD 8.2). [amount] is always positive; the type decides its direction.
 * [date] is the financial date chosen by the user, kept separate from [createdAt]/[updatedAt]
 * and timezone-free so it never shifts (PRD 8.5).
 */
sealed interface Transaction {
    val id: TransactionId
    val amount: Money
    val title: String
    val date: LocalDate
    val notes: String?
    val createdBy: UserId
    val createdAt: Instant
    val updatedAt: Instant

    /** How this transaction changes [accountId]'s balance (PRD 9.1). */
    fun effectOn(accountId: AccountId): Money

    data class Income(
        override val id: TransactionId,
        override val amount: Money,
        override val title: String,
        override val date: LocalDate,
        val accountId: AccountId,
        val categoryId: CategoryId,
        val source: String,
        override val notes: String?,
        override val createdBy: UserId,
        override val createdAt: Instant,
        override val updatedAt: Instant,
    ) : Transaction {
        override fun effectOn(accountId: AccountId): Money =
            if (accountId == this.accountId) amount else Money.ZERO
    }

    data class Expense(
        override val id: TransactionId,
        override val amount: Money,
        override val title: String,
        override val date: LocalDate,
        val accountId: AccountId,
        val categoryId: CategoryId,
        val merchant: String?,
        override val notes: String?,
        override val createdBy: UserId,
        override val createdAt: Instant,
        override val updatedAt: Instant,
    ) : Transaction {
        override fun effectOn(accountId: AccountId): Money =
            if (accountId == this.accountId) -amount else Money.ZERO
    }

    /**
     * One record with one stable ID covers both sides, so editing or deleting it always
     * updates both accounts together (PRD 9.2). Never counted as income or expense.
     */
    data class Transfer(
        override val id: TransactionId,
        override val amount: Money,
        override val title: String,
        override val date: LocalDate,
        val fromAccountId: AccountId,
        val toAccountId: AccountId,
        override val notes: String?,
        override val createdBy: UserId,
        override val createdAt: Instant,
        override val updatedAt: Instant,
    ) : Transaction {
        init {
            require(fromAccountId != toAccountId) { "Transfer accounts must differ" }
        }

        override fun effectOn(accountId: AccountId): Money = when (accountId) {
            fromAccountId -> -amount
            toAccountId -> amount
            else -> Money.ZERO
        }
    }
}
