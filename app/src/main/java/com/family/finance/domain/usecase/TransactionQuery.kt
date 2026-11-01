package com.family.finance.domain.usecase

import com.family.finance.domain.calc.DateRange
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.CategoryId
import com.family.finance.domain.model.Money
import com.family.finance.domain.model.Transaction

enum class TypeFilter { ALL, INCOME, EXPENSE, TRANSFER }

enum class SortOrder { DATE_DESC, DATE_ASC, AMOUNT_DESC, AMOUNT_ASC }

/** Filters for transaction history (PRD 6.7). Every field is optional. */
data class TransactionQuery(
    val type: TypeFilter = TypeFilter.ALL,
    val text: String = "",
    val range: DateRange? = null,
    val accountId: AccountId? = null,
    val categoryId: CategoryId? = null,
    val minAmount: Money? = null,
    val maxAmount: Money? = null,
    val sort: SortOrder = SortOrder.DATE_DESC,
) {
    /**
     * [categoryName] supplies the displayed (possibly localized) category name so search
     * matches what the user sees. Search works on Tamil text directly, without
     * transliteration (PRD 7.5).
     */
    fun apply(
        transactions: Iterable<Transaction>,
        categoryName: (CategoryId) -> String = { "" },
    ): List<Transaction> {
        val filtered = transactions.filter { t ->
            matchesType(t) &&
                (range == null || t.date in range) &&
                (accountId == null || touchesAccount(t, accountId)) &&
                (categoryId == null || categoryOf(t) == categoryId) &&
                (minAmount == null || t.amount >= minAmount) &&
                (maxAmount == null || t.amount <= maxAmount) &&
                TextSearch.matches(searchableText(t, categoryName), text)
        }
        val comparator = when (sort) {
            SortOrder.DATE_DESC -> compareByDescending<Transaction> { it.date }.thenByDescending { it.createdAt }
            SortOrder.DATE_ASC -> compareBy<Transaction> { it.date }.thenBy { it.createdAt }
            SortOrder.AMOUNT_DESC -> compareByDescending<Transaction> { it.amount }.thenByDescending { it.date }
            SortOrder.AMOUNT_ASC -> compareBy<Transaction> { it.amount }.thenByDescending { it.date }
        }
        return filtered.sortedWith(comparator)
    }

    private fun matchesType(t: Transaction) = when (type) {
        TypeFilter.ALL -> true
        TypeFilter.INCOME -> t is Transaction.Income
        TypeFilter.EXPENSE -> t is Transaction.Expense
        TypeFilter.TRANSFER -> t is Transaction.Transfer
    }

    private fun searchableText(t: Transaction, categoryName: (CategoryId) -> String): String {
        val parts = buildList {
            add(t.title)
            t.notes?.let(::add)
            when (t) {
                is Transaction.Income -> { add(t.source); add(categoryName(t.categoryId)) }
                is Transaction.Expense -> { t.merchant?.let(::add); add(categoryName(t.categoryId)) }
                is Transaction.Transfer -> Unit
            }
        }
        return parts.joinToString("\n")
    }

    companion object {
        fun touchesAccount(t: Transaction, id: AccountId): Boolean = when (t) {
            is Transaction.Income -> t.accountId == id
            is Transaction.Expense -> t.accountId == id
            is Transaction.Transfer -> t.fromAccountId == id || t.toAccountId == id
        }

        fun categoryOf(t: Transaction): CategoryId? = when (t) {
            is Transaction.Income -> t.categoryId
            is Transaction.Expense -> t.categoryId
            is Transaction.Transfer -> null
        }
    }
}
