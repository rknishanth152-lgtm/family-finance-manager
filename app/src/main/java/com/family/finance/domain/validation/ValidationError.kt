package com.family.finance.domain.validation

/** Which form field failed. The UI maps each (field, reason) pair to a string resource. */
enum class Field { AMOUNT, TITLE, CATEGORY, INCOME_SOURCE, ACCOUNT, FROM_ACCOUNT, TO_ACCOUNT, DATE }

enum class Reason {
    REQUIRED,
    /** Not a number, or more than two decimal places. */
    INVALID_FORMAT,
    NOT_POSITIVE,
    NOT_FOUND,
    INACTIVE,
    ARCHIVED,
    WRONG_KIND,
    SAME_AS_SOURCE,
}

data class ValidationError(val field: Field, val reason: Reason)
