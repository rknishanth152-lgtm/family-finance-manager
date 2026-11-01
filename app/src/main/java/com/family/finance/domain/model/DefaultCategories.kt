package com.family.finance.domain.model

/** Suggested categories from PRD 6.2 and 6.3. IDs are stable and equal to the system key. */
object DefaultCategories {

    val INCOME_KEYS = listOf(
        "agricultural_income", "business_income", "salary", "interest_received",
        "investment_income", "family_received", "loan_received", "refund", "other_income",
    )

    val EXPENSE_KEYS = listOf(
        // Household
        "groceries", "food", "household_supplies", "electricity", "water", "internet",
        "mobile_recharge", "rent",
        // Agriculture
        "seeds", "fertilizers", "pesticides", "agri_labour", "machinery", "equipment",
        "irrigation", "transport", "repairs", "other_agri",
        // Personal
        "medical", "education", "travel", "vehicle_maintenance", "insurance", "entertainment",
        "gifts", "other_expenses",
        // Financial
        "loan_principal", "loan_interest", "bank_charges", "taxes",
    )

    private val LOAN_KEYS = setOf("loan_received", "loan_principal")

    fun idFor(key: String) = CategoryId("sys.$key")

    val all: List<Category> =
        INCOME_KEYS.map { category(it, CategoryKind.INCOME) } +
            EXPENSE_KEYS.map { category(it, CategoryKind.EXPENSE) }

    private fun category(key: String, kind: CategoryKind) = Category(
        id = idFor(key),
        name = "",
        kind = kind,
        isArchived = false,
        systemKey = key,
        isLoan = key in LOAN_KEYS,
    )
}
