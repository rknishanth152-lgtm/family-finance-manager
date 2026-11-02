package com.family.finance.domain.usecase

import com.family.finance.domain.model.Money
import com.family.finance.domain.model.Transaction
import com.family.finance.domain.model.TransactionId
import com.family.finance.domain.model.UserId
import com.family.finance.domain.repository.AccountRepository
import com.family.finance.domain.repository.CategoryRepository
import com.family.finance.domain.repository.TransactionRepository
import com.family.finance.domain.validation.ExpenseDraft
import com.family.finance.domain.validation.ExpenseValidator
import com.family.finance.domain.validation.IncomeDraft
import com.family.finance.domain.validation.IncomeValidator
import com.family.finance.domain.validation.TransferDraft
import com.family.finance.domain.validation.TransferValidator
import com.family.finance.domain.validation.ValidationError
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.AccountType
import com.family.finance.domain.model.CategoryId
import com.family.finance.domain.model.DefaultCategories
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant

sealed interface SaveResult {
    data class Saved(val transaction: Transaction) : SaveResult
    data class Invalid(val errors: List<ValidationError>) : SaveResult
}

/**
 * Validates and stores transactions. The caller creates the [TransactionId] once per form,
 * so saving twice writes the same record instead of a duplicate (PRD 9.3). Editing keeps
 * the original creator and creation time; a transfer is one record, so editing or deleting
 * it always moves both account balances together (PRD 9.2).
 */
class TransactionService(
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val currentUser: () -> UserId,
    private val clock: Clock,
) {

    suspend fun saveIncome(id: TransactionId, picked: IncomeDraft): SaveResult {
        val draft = picked.copy(
            accountId = picked.accountId ?: cashAccount(),
            categoryId = picked.categoryId ?: DefaultCategories.idFor("other_income"),
        )
        val errors = IncomeValidator.validate(draft, accountsById(listOf(draft.accountId)), categoriesById(listOf(draft.categoryId)))
        if (errors.isNotEmpty()) return SaveResult.Invalid(errors)
        val (createdBy, createdAt, now) = stamps(id)
        return store(
            Transaction.Income(
                id = id,
                amount = Money.parse(draft.amountText)!!,
                title = draft.title.trim(),
                date = draft.date!!,
                accountId = draft.accountId!!,
                categoryId = draft.categoryId!!,
                source = draft.source.trim(),
                notes = draft.notes.trim().ifEmpty { null },
                createdBy = createdBy,
                createdAt = createdAt,
                updatedAt = now,
            ),
        )
    }

    suspend fun saveExpense(id: TransactionId, picked: ExpenseDraft): SaveResult {
        val draft = picked.copy(
            accountId = picked.accountId ?: cashAccount(),
            categoryId = picked.categoryId ?: DefaultCategories.idFor("other_expenses"),
        )
        val errors = ExpenseValidator.validate(draft, accountsById(listOf(draft.accountId)), categoriesById(listOf(draft.categoryId)))
        if (errors.isNotEmpty()) return SaveResult.Invalid(errors)
        val (createdBy, createdAt, now) = stamps(id)
        return store(
            Transaction.Expense(
                id = id,
                amount = Money.parse(draft.amountText)!!,
                title = draft.title.trim(),
                date = draft.date!!,
                accountId = draft.accountId!!,
                categoryId = draft.categoryId!!,
                merchant = draft.merchant.trim().ifEmpty { null },
                notes = draft.notes.trim().ifEmpty { null },
                createdBy = createdBy,
                createdAt = createdAt,
                updatedAt = now,
            ),
        )
    }

    suspend fun saveTransfer(id: TransactionId, draft: TransferDraft): SaveResult {
        val errors = TransferValidator.validate(draft, accountsById(listOf(draft.fromAccountId, draft.toAccountId)))
        if (errors.isNotEmpty()) return SaveResult.Invalid(errors)
        val (createdBy, createdAt, now) = stamps(id)
        val description = draft.description.trim()
        return store(
            Transaction.Transfer(
                id = id,
                amount = Money.parse(draft.amountText)!!,
                title = description,
                date = draft.date!!,
                fromAccountId = draft.fromAccountId!!,
                toAccountId = draft.toAccountId!!,
                notes = description.ifEmpty { null },
                createdBy = createdBy,
                createdAt = createdAt,
                updatedAt = now,
            ),
        )
    }

    suspend fun delete(id: TransactionId) = transactions.delete(id)

    /**
     * Account and category may be left blank on income and expense forms. A blank account
     * counts as cash, so every entry still belongs to an account and balances stay derived
     * from accounts alone; a blank category becomes the built-in "Other" one. With several
     * cash accounts the oldest is used; with none, the person must pick an account.
     */
    private suspend fun cashAccount(): AccountId? =
        accounts.observeAll().first()
            .filter { it.isActive && it.type == AccountType.CASH }
            .minWithOrNull(compareBy({ it.createdAt }, { it.id.value }))
            ?.id

    /**
     * "This version is correct" after both phones edited the same entry: saving it again
     * starts a newer round of edits, which closes the conflict (PRD 9.4, 11.5).
     */
    /** Brings back a version from the edit log, e.g. one deleted on the other phone (PRD 1.4.2). */
    suspend fun restore(version: Transaction) {
        transactions.upsert(
            when (version) {
                is Transaction.Income -> version.copy(updatedAt = clock.instant())
                is Transaction.Expense -> version.copy(updatedAt = clock.instant())
                is Transaction.Transfer -> version.copy(updatedAt = clock.instant())
            },
        )
    }

    suspend fun confirm(id: TransactionId) {
        val current = transactions.get(id) ?: return
        val now = clock.instant()
        transactions.upsert(
            when (current) {
                is Transaction.Income -> current.copy(updatedAt = now)
                is Transaction.Expense -> current.copy(updatedAt = now)
                is Transaction.Transfer -> current.copy(updatedAt = now)
            },
        )
    }

    private suspend fun store(transaction: Transaction): SaveResult {
        transactions.upsert(transaction)
        return SaveResult.Saved(transaction)
    }

    private data class Stamps(val createdBy: UserId, val createdAt: Instant, val now: Instant)

    private suspend fun stamps(id: TransactionId): Stamps {
        val now = clock.instant()
        val existing = transactions.get(id)
        return Stamps(existing?.createdBy ?: currentUser(), existing?.createdAt ?: now, now)
    }

    /*
     * Looks up only the records the draft refers to. Reading a whole live list could give
     * an empty first result on a phone whose local copy is still filling in, which would
     * wrongly reject a valid account.
     */
    private suspend fun accountsById(ids: List<AccountId?>) =
        ids.filterNotNull().distinct().mapNotNull { accounts.get(it) }.associateBy { it.id }

    private suspend fun categoriesById(ids: List<CategoryId?>) =
        ids.filterNotNull().distinct().mapNotNull { categories.get(it) }.associateBy { it.id }
}
