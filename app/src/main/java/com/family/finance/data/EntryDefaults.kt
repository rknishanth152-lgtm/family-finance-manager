package com.family.finance.data

import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.CategoryId
import com.family.finance.domain.model.CategoryKind

/**
 * Remembers the last account and category used per entry type, so the next entry needs
 * fewer taps (a common request from users of the app being replaced).
 */
class EntryDefaults {
    data class Last(val accountId: AccountId?, val categoryId: CategoryId?)
    data class LastTransfer(val fromAccountId: AccountId?, val toAccountId: AccountId?)

    private val last = mutableMapOf<CategoryKind, Last>()
    private var lastTransfer = LastTransfer(null, null)

    @Synchronized fun lastFor(kind: CategoryKind): Last = last[kind] ?: Last(null, null)

    @Synchronized fun remember(kind: CategoryKind, accountId: AccountId, categoryId: CategoryId) {
        last[kind] = Last(accountId, categoryId)
    }

    @Synchronized fun lastTransfer(): LastTransfer = lastTransfer

    @Synchronized fun rememberTransfer(from: AccountId, to: AccountId) {
        lastTransfer = LastTransfer(from, to)
    }
}
