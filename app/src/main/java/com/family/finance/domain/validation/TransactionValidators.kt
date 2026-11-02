package com.family.finance.domain.validation

import com.family.finance.domain.model.Account
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.Category
import com.family.finance.domain.model.CategoryId
import com.family.finance.domain.model.CategoryKind
import com.family.finance.domain.model.Money
import java.time.LocalDate

/** Income rules, PRD 6.2. An empty result means the draft is valid. */
object IncomeValidator {
    fun validate(
        draft: IncomeDraft,
        accounts: Map<AccountId, Account>,
        categories: Map<CategoryId, Category>,
    ): List<ValidationError> = buildList {
        checkAmount(draft.amountText)
        checkTitle(draft.title)
        checkCategory(draft.categoryId, CategoryKind.INCOME, categories)
        // "Received from" is optional: a blank source is kept blank.
        checkDate(draft.date)
        checkAccount(Field.ACCOUNT, draft.accountId, accounts)
    }
}

/** Expense rules, PRD 6.3. An empty result means the draft is valid. */
object ExpenseValidator {
    fun validate(
        draft: ExpenseDraft,
        accounts: Map<AccountId, Account>,
        categories: Map<CategoryId, Category>,
    ): List<ValidationError> = buildList {
        checkAmount(draft.amountText)
        checkTitle(draft.title)
        checkCategory(draft.categoryId, CategoryKind.EXPENSE, categories)
        checkDate(draft.date)
        checkAccount(Field.ACCOUNT, draft.accountId, accounts)
    }
}

/** Transfer rules, PRD 6.4. An empty result means the draft is valid. */
object TransferValidator {
    fun validate(
        draft: TransferDraft,
        accounts: Map<AccountId, Account>,
    ): List<ValidationError> = buildList {
        checkAmount(draft.amountText)
        checkAccount(Field.FROM_ACCOUNT, draft.fromAccountId, accounts)
        checkAccount(Field.TO_ACCOUNT, draft.toAccountId, accounts)
        if (draft.fromAccountId != null && draft.fromAccountId == draft.toAccountId) {
            add(ValidationError(Field.TO_ACCOUNT, Reason.SAME_AS_SOURCE))
        }
        checkDate(draft.date)
    }
}

private fun MutableList<ValidationError>.checkAmount(text: String) {
    if (text.isBlank()) {
        add(ValidationError(Field.AMOUNT, Reason.REQUIRED))
        return
    }
    val amount = Money.parse(text)
    when {
        amount == null -> add(ValidationError(Field.AMOUNT, Reason.INVALID_FORMAT))
        !amount.isPositive -> add(ValidationError(Field.AMOUNT, Reason.NOT_POSITIVE))
        !amount.isWithinLimit -> add(ValidationError(Field.AMOUNT, Reason.TOO_LARGE))
    }
}

private fun MutableList<ValidationError>.checkTitle(title: String) {
    if (title.isBlank()) add(ValidationError(Field.TITLE, Reason.REQUIRED))
}

private fun MutableList<ValidationError>.checkDate(date: LocalDate?) {
    if (date == null) add(ValidationError(Field.DATE, Reason.REQUIRED))
}

private fun MutableList<ValidationError>.checkCategory(
    id: CategoryId?,
    expected: CategoryKind,
    categories: Map<CategoryId, Category>,
) {
    if (id == null) {
        add(ValidationError(Field.CATEGORY, Reason.REQUIRED))
        return
    }
    val category = categories[id]
    when {
        category == null -> add(ValidationError(Field.CATEGORY, Reason.NOT_FOUND))
        category.kind != expected -> add(ValidationError(Field.CATEGORY, Reason.WRONG_KIND))
        category.isArchived -> add(ValidationError(Field.CATEGORY, Reason.ARCHIVED))
    }
}

private fun MutableList<ValidationError>.checkAccount(
    field: Field,
    id: AccountId?,
    accounts: Map<AccountId, Account>,
) {
    if (id == null) {
        add(ValidationError(field, Reason.REQUIRED))
        return
    }
    val account = accounts[id]
    when {
        account == null -> add(ValidationError(field, Reason.NOT_FOUND))
        !account.isActive -> add(ValidationError(field, Reason.INACTIVE))
    }
}
