package com.family.finance.domain.model

enum class CategoryKind { INCOME, EXPENSE }

/** Archived categories stay resolvable for history but are not offered for new entries (PRD 6.6). */
data class Category(
    val id: CategoryId,
    val name: String,
    val kind: CategoryKind,
    val isArchived: Boolean,
)
