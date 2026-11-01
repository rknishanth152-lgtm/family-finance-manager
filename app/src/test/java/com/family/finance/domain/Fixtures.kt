package com.family.finance.domain

import com.family.finance.domain.model.Account
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.AccountType
import com.family.finance.domain.model.Category
import com.family.finance.domain.model.CategoryId
import com.family.finance.domain.model.CategoryKind
import com.family.finance.domain.model.Money
import com.family.finance.domain.model.Transaction
import com.family.finance.domain.model.TransactionId
import com.family.finance.domain.model.UserId
import java.time.Instant
import java.time.LocalDate

object Fixtures {
    val NOW: Instant = Instant.parse("2026-11-01T00:00:00Z")
    val FATHER = UserId("father")

    val CASH_ID = AccountId("cash")
    val BANK_ID = AccountId("bank")
    val SALARY_ID = CategoryId("salary")
    val GROCERIES_ID = CategoryId("groceries")

    fun rupees(r: Long) = Money.ofRupees(r)

    fun date(text: String): LocalDate = LocalDate.parse(text)

    fun account(
        id: AccountId,
        opening: Money = Money.ZERO,
        openingDate: LocalDate = date("2026-01-01"),
        active: Boolean = true,
    ) = Account(
        id = id,
        name = id.value,
        type = AccountType.CASH,
        openingBalance = opening,
        openingBalanceDate = openingDate,
        isActive = active,
        createdAt = NOW,
        updatedAt = NOW,
    )

    fun category(id: CategoryId, kind: CategoryKind, archived: Boolean = false) =
        Category(id = id, name = id.value, kind = kind, isArchived = archived)

    private var counter = 0
    private fun nextId() = TransactionId("t${++counter}")

    fun income(amount: Money, account: AccountId, on: String = "2026-03-15") = Transaction.Income(
        id = nextId(), amount = amount, title = "வருமானம்", date = date(on),
        accountId = account, categoryId = SALARY_ID, source = "Employer",
        notes = null, createdBy = FATHER, createdAt = NOW, updatedAt = NOW,
    )

    fun expense(amount: Money, account: AccountId, on: String = "2026-03-15") = Transaction.Expense(
        id = nextId(), amount = amount, title = "மளிகை", date = date(on),
        accountId = account, categoryId = GROCERIES_ID, merchant = null,
        notes = null, createdBy = FATHER, createdAt = NOW, updatedAt = NOW,
    )

    fun transfer(amount: Money, from: AccountId, to: AccountId, on: String = "2026-03-15") = Transaction.Transfer(
        id = nextId(), amount = amount, title = "Transfer", date = date(on),
        fromAccountId = from, toAccountId = to,
        notes = null, createdBy = FATHER, createdAt = NOW, updatedAt = NOW,
    )
}
