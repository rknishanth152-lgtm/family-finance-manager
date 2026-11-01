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

    suspend fun saveIncome(id: TransactionId, draft: IncomeDraft): SaveResult {
        val errors = IncomeValidator.validate(draft, accountMap(), categoryMap())
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

    suspend fun saveExpense(id: TransactionId, draft: ExpenseDraft): SaveResult {
        val errors = ExpenseValidator.validate(draft, accountMap(), categoryMap())
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
        val errors = TransferValidator.validate(draft, accountMap())
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

    private suspend fun accountMap() = accounts.observeAll().first().associateBy { it.id }
    private suspend fun categoryMap() = categories.observeAll().first().associateBy { it.id }
}
