package com.family.finance.domain.validation

import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.CategoryId
import java.time.LocalDate

/*
 * Unvalidated form input. Amounts stay as the text the user typed so that parsing,
 * format and sign errors are all reported by the validators.
 */

data class IncomeDraft(
    val amountText: String,
    val title: String,
    val categoryId: CategoryId?,
    val source: String,
    val date: LocalDate?,
    val accountId: AccountId?,
)

data class ExpenseDraft(
    val amountText: String,
    val title: String,
    val categoryId: CategoryId?,
    val date: LocalDate?,
    val accountId: AccountId?,
)

data class TransferDraft(
    val amountText: String,
    val fromAccountId: AccountId?,
    val toAccountId: AccountId?,
    val date: LocalDate?,
)
