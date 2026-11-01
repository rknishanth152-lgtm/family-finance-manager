package com.family.finance.domain.model

import java.util.UUID

/*
 * Stable identifiers (PRD 8.4). Records reference each other by ID, never by display name,
 * so renaming a category or account cannot break history. A transaction's ID is created
 * once, before the first save attempt, so retries write the same record (PRD 9.3).
 */

@JvmInline
value class TransactionId(val value: String) {
    companion object {
        fun generate() = TransactionId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class AccountId(val value: String) {
    companion object {
        fun generate() = AccountId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class CategoryId(val value: String) {
    companion object {
        fun generate() = CategoryId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class UserId(val value: String)
