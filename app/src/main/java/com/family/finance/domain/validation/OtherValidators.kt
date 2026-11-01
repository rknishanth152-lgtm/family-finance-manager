package com.family.finance.domain.validation

import com.family.finance.domain.model.Money

object AccountValidator {
    fun validate(draft: AccountDraft): List<ValidationError> = buildList {
        if (draft.name.isBlank()) add(ValidationError(Field.NAME, Reason.REQUIRED))
        if (draft.openingBalanceText.isNotBlank() && Money.parse(draft.openingBalanceText) == null) {
            add(ValidationError(Field.OPENING_BALANCE, Reason.INVALID_FORMAT))
        }
        if (draft.openingBalanceDate == null) add(ValidationError(Field.DATE, Reason.REQUIRED))
    }
}

object CategoryNameValidator {
    fun validate(name: String): List<ValidationError> =
        if (name.isBlank()) listOf(ValidationError(Field.NAME, Reason.REQUIRED)) else emptyList()
}

object NoteValidator {
    fun validate(draft: NoteDraft): List<ValidationError> = buildList {
        if (draft.title.isBlank()) add(ValidationError(Field.TITLE, Reason.REQUIRED))
        if (draft.date == null) add(ValidationError(Field.DATE, Reason.REQUIRED))
    }
}

/** A plausible email for inviting a family member (Google checks the real account at sign-in). */
object EmailValidator {
    private val pattern = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]{2,}$""")
    fun isValid(email: String): Boolean = pattern.matches(email.trim())
}
