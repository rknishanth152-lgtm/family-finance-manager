package com.family.finance.domain.model

enum class CategoryKind { INCOME, EXPENSE }

/**
 * Archived categories stay resolvable for history but are not offered for new entries (PRD 6.6).
 *
 * Built-in categories carry a [systemKey] and a blank [name]; the UI shows them in the
 * viewer's own language, so one parent's language choice never renames anything for the
 * other (PRD 7.1). Renaming a built-in category fills in [name], which then wins.
 *
 * [isLoan] marks loan receipts and principal repayments so reports can keep them apart
 * from earned income and ordinary spending (PRD 6.2, 6.3).
 */
data class Category(
    val id: CategoryId,
    val name: String,
    val kind: CategoryKind,
    val isArchived: Boolean,
    val systemKey: String? = null,
    val isLoan: Boolean = false,
)
