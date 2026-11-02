package com.family.finance.ui.form

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.finance.data.preferences.EntryDefaults
import com.family.finance.domain.model.Account
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.Category
import com.family.finance.domain.model.CategoryId
import com.family.finance.domain.model.CategoryKind
import com.family.finance.domain.model.MoneyFormatter
import com.family.finance.domain.model.Transaction
import com.family.finance.domain.model.TransactionId
import com.family.finance.domain.repository.AccountRepository
import com.family.finance.domain.repository.CategoryRepository
import com.family.finance.domain.repository.TransactionRepository
import com.family.finance.domain.usecase.SaveResult
import com.family.finance.domain.usecase.TransactionService
import com.family.finance.domain.validation.ExpenseDraft
import com.family.finance.domain.validation.IncomeDraft
import com.family.finance.domain.validation.TransferDraft
import com.family.finance.domain.validation.ValidationError
import com.family.finance.ui.navigation.FormKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class FormFields(
    val amountText: String = "",
    val title: String = "",
    val categoryId: CategoryId? = null,
    val source: String = "",
    val merchant: String = "",
    val accountId: AccountId? = null,
    val toAccountId: AccountId? = null,
    val date: LocalDate? = null,
    val notes: String = "",
)

data class FormState(
    val kind: FormKind,
    val isEdit: Boolean,
    val loaded: Boolean = false,
    val fields: FormFields = FormFields(),
    val showMore: Boolean = false,
    val errors: List<ValidationError> = emptyList(),
    val saving: Boolean = false,
    val savedId: TransactionId? = null,
)

data class FormOptions(val accounts: List<Account> = emptyList(), val categories: List<Category> = emptyList())

/**
 * One ViewModel for income, expense and transfer forms. The transaction ID is fixed for
 * the life of the form, and [save] ignores repeat taps while a save is in flight, so a
 * double tap can never create two records (PRD 6.3, 9.3). Entered values survive
 * validation errors (PRD 14.3).
 */
class TransactionFormViewModel(
    private val kind: FormKind,
    editId: TransactionId?,
    private val service: TransactionService,
    private val transactions: TransactionRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    private val defaults: EntryDefaults,
    private val today: () -> LocalDate,
) : ViewModel() {

    private var id: TransactionId = editId ?: TransactionId.generate()

    private val _state = MutableStateFlow(FormState(kind = kind, isEdit = editId != null))
    val state: StateFlow<FormState> = _state.asStateFlow()

    val options: StateFlow<FormOptions> = combine(
        accounts.observeAll(), categories.observeAll(), _state,
    ) { accts, cats, s ->
        val selected = setOfNotNull(s.fields.accountId, s.fields.toAccountId)
        FormOptions(
            accounts = accts.filter { it.isActive || it.id in selected }.sortedBy { it.name.lowercase() },
            categories = kind.categoryKind?.let { k ->
                cats.filter { it.kind == k && (!it.isArchived || it.id == s.fields.categoryId) }
            } ?: emptyList(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FormOptions())

    init {
        viewModelScope.launch {
            val fields = if (editId != null) {
                transactions.get(editId)?.toFields() ?: FormFields(date = today())
            } else {
                newFields(accounts.observeAll().first())
            }
            _state.update { it.copy(loaded = true, fields = fields, showMore = fields.notes.isNotBlank() || fields.merchant.isNotBlank()) }
        }
    }

    fun edit(transform: (FormFields) -> FormFields) {
        _state.update { s ->
            val updated = transform(s.fields)
            // Clear errors for fields the user just changed, keep the rest visible.
            s.copy(fields = updated, errors = s.errors.filterNot { changed(it, s.fields, updated) })
        }
    }

    fun toggleMore() = _state.update { it.copy(showMore = !it.showMore) }

    fun save() {
        val current = _state.value
        if (current.saving || current.savedId != null || !current.loaded) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val f = current.fields
            val result = when (kind) {
                FormKind.INCOME -> service.saveIncome(id, IncomeDraft(f.amountText, f.title, f.categoryId, f.source, f.date, f.accountId, f.notes))
                FormKind.EXPENSE -> service.saveExpense(id, ExpenseDraft(f.amountText, f.title, f.categoryId, f.date, f.accountId, f.merchant, f.notes))
                FormKind.TRANSFER -> service.saveTransfer(id, TransferDraft(f.amountText, f.accountId, f.toAccountId, f.date, f.notes))
            }
            when (result) {
                is SaveResult.Saved -> {
                    rememberDefaults(f)
                    _state.update { it.copy(saving = false, errors = emptyList(), savedId = result.transaction.id) }
                }
                is SaveResult.Invalid -> _state.update { it.copy(saving = false, errors = result.errors) }
            }
        }
    }

    /** Start a fresh entry, keeping account, category and date for quick repeated entry. */
    fun addAnother() {
        id = TransactionId.generate()
        _state.update { s ->
            s.copy(
                isEdit = false,
                savedId = null,
                errors = emptyList(),
                showMore = false,
                fields = FormFields(
                    accountId = s.fields.accountId,
                    toAccountId = s.fields.toAccountId,
                    categoryId = s.fields.categoryId,
                    date = s.fields.date,
                ),
            )
        }
    }

    private fun rememberDefaults(f: FormFields) {
        when (kind) {
            FormKind.INCOME, FormKind.EXPENSE ->
                if (f.accountId != null && f.categoryId != null) defaults.remember(kind.categoryKind!!, f.accountId, f.categoryId)
            FormKind.TRANSFER ->
                if (f.accountId != null && f.toAccountId != null) defaults.rememberTransfer(f.accountId, f.toAccountId)
        }
    }

    private fun newFields(accounts: List<Account>): FormFields {
        val active = accounts.filter { it.isActive }.map { it.id }.toSet()
        val only = active.singleOrNull()
        return when (kind) {
            FormKind.INCOME, FormKind.EXPENSE -> {
                val last = defaults.lastFor(kind.categoryKind!!)
                FormFields(
                    date = today(),
                    accountId = last.accountId?.takeIf { it in active } ?: only,
                    categoryId = last.categoryId,
                )
            }
            FormKind.TRANSFER -> {
                val last = defaults.lastTransfer()
                FormFields(
                    date = today(),
                    accountId = last.fromAccountId?.takeIf { it in active },
                    toAccountId = last.toAccountId?.takeIf { it in active },
                )
            }
        }
    }

    private fun Transaction.toFields(): FormFields = when (this) {
        is Transaction.Income -> FormFields(
            amountText = MoneyFormatter.plain(amount), title = title, categoryId = categoryId,
            source = source, accountId = accountId, date = date, notes = notes.orEmpty(),
        )
        is Transaction.Expense -> FormFields(
            amountText = MoneyFormatter.plain(amount), title = title, categoryId = categoryId,
            merchant = merchant.orEmpty(), accountId = accountId, date = date, notes = notes.orEmpty(),
        )
        is Transaction.Transfer -> FormFields(
            amountText = MoneyFormatter.plain(amount), accountId = fromAccountId,
            toAccountId = toAccountId, date = date, notes = notes.orEmpty(),
        )
    }

    private fun changed(error: ValidationError, old: FormFields, new: FormFields): Boolean = when (error.field) {
        com.family.finance.domain.validation.Field.AMOUNT -> old.amountText != new.amountText
        com.family.finance.domain.validation.Field.TITLE -> old.title != new.title
        com.family.finance.domain.validation.Field.CATEGORY -> old.categoryId != new.categoryId
        com.family.finance.domain.validation.Field.INCOME_SOURCE -> old.source != new.source
        com.family.finance.domain.validation.Field.ACCOUNT,
        com.family.finance.domain.validation.Field.FROM_ACCOUNT -> old.accountId != new.accountId
        com.family.finance.domain.validation.Field.TO_ACCOUNT ->
            old.toAccountId != new.toAccountId || old.accountId != new.accountId
        com.family.finance.domain.validation.Field.DATE -> old.date != new.date
        else -> false
    }
}

val FormKind.categoryKind: CategoryKind?
    get() = when (this) {
        FormKind.INCOME -> CategoryKind.INCOME
        FormKind.EXPENSE -> CategoryKind.EXPENSE
        FormKind.TRANSFER -> null
    }
