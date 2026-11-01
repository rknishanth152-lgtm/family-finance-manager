package com.family.finance.domain.usecase

import com.family.finance.domain.model.Account
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.Category
import com.family.finance.domain.model.CategoryId
import com.family.finance.domain.model.CategoryKind
import com.family.finance.domain.model.Money
import com.family.finance.domain.model.NoteEntry
import com.family.finance.domain.model.NoteId
import com.family.finance.domain.model.UserId
import com.family.finance.domain.repository.AccountRepository
import com.family.finance.domain.repository.CategoryRepository
import com.family.finance.domain.repository.NoteRepository
import com.family.finance.domain.validation.AccountDraft
import com.family.finance.domain.validation.AccountValidator
import com.family.finance.domain.validation.CategoryNameValidator
import com.family.finance.domain.validation.NoteDraft
import com.family.finance.domain.validation.NoteValidator
import com.family.finance.domain.validation.ValidationError
import java.time.Clock

sealed interface RecordResult<out T> {
    data class Saved<T>(val value: T) : RecordResult<T>
    data class Invalid(val errors: List<ValidationError>) : RecordResult<Nothing>
}

/** Accounts are archived, never deleted, so history keeps its references (PRD 6.5). */
class AccountService(private val accounts: AccountRepository, private val clock: Clock) {

    suspend fun save(id: AccountId, draft: AccountDraft): RecordResult<Account> {
        val errors = AccountValidator.validate(draft)
        if (errors.isNotEmpty()) return RecordResult.Invalid(errors)
        val now = clock.instant()
        val existing = accounts.get(id)
        val account = Account(
            id = id,
            name = draft.name.trim(),
            type = draft.type,
            openingBalance = draft.openingBalanceText.takeIf { it.isNotBlank() }?.let { Money.parse(it)!! } ?: Money.ZERO,
            openingBalanceDate = draft.openingBalanceDate!!,
            isActive = existing?.isActive ?: true,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        accounts.upsert(account)
        return RecordResult.Saved(account)
    }

    suspend fun setActive(id: AccountId, active: Boolean) {
        val existing = accounts.get(id) ?: return
        accounts.upsert(existing.copy(isActive = active, updatedAt = clock.instant()))
    }
}

/** Renaming keeps the ID, so historical transactions follow the new name (PRD 8.4). */
class CategoryService(private val categories: CategoryRepository) {

    suspend fun create(id: CategoryId, name: String, kind: CategoryKind): RecordResult<Category> {
        val errors = CategoryNameValidator.validate(name)
        if (errors.isNotEmpty()) return RecordResult.Invalid(errors)
        val category = Category(id = id, name = name.trim(), kind = kind, isArchived = false)
        categories.upsert(category)
        return RecordResult.Saved(category)
    }

    suspend fun rename(id: CategoryId, name: String): RecordResult<Category> {
        val errors = CategoryNameValidator.validate(name)
        if (errors.isNotEmpty()) return RecordResult.Invalid(errors)
        val existing = categories.get(id) ?: return RecordResult.Invalid(emptyList())
        val renamed = existing.copy(name = name.trim())
        categories.upsert(renamed)
        return RecordResult.Saved(renamed)
    }

    suspend fun setArchived(id: CategoryId, archived: Boolean) {
        val existing = categories.get(id) ?: return
        categories.upsert(existing.copy(isArchived = archived))
    }
}

class NoteService(
    private val notes: NoteRepository,
    private val currentUser: () -> UserId,
    private val clock: Clock,
) {
    suspend fun save(id: NoteId, draft: NoteDraft): RecordResult<NoteEntry> {
        val errors = NoteValidator.validate(draft)
        if (errors.isNotEmpty()) return RecordResult.Invalid(errors)
        val now = clock.instant()
        val existing = notes.get(id)
        val note = NoteEntry(
            id = id,
            title = draft.title.trim(),
            description = draft.description.trim(),
            date = draft.date!!,
            createdBy = existing?.createdBy ?: currentUser(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        notes.upsert(note)
        return RecordResult.Saved(note)
    }

    suspend fun delete(id: NoteId) = notes.delete(id)
}
