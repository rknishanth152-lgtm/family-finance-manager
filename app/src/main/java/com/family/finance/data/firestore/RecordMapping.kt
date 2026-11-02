package com.family.finance.data.firestore

import com.family.finance.domain.model.Account
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.AccountType
import com.family.finance.domain.model.Category
import com.family.finance.domain.model.CategoryId
import com.family.finance.domain.model.CategoryKind
import com.family.finance.domain.model.Money
import com.family.finance.domain.model.NoteEntry
import com.family.finance.domain.model.NoteId
import com.family.finance.domain.model.Transaction
import com.family.finance.domain.model.TransactionId
import com.family.finance.domain.model.UserId
import java.time.Instant
import java.time.LocalDate

/**
 * Converts domain records to and from Firestore field maps. Pure Kotlin (Instant for
 * timestamps; the Firestore layer swaps in Timestamp), so it is unit-tested directly.
 *
 * Money is stored as whole paise (Long) and dates as "YYYY-MM-DD" text, so neither can
 * pick up rounding or timezone shifts (PRD 8.3, 8.5). Field names match firestore.rules.
 */
object RecordMapping {
    const val SCHEMA_VERSION = 1L

    // --- transactions ---

    fun encode(t: Transaction): Map<String, Any?> = buildMap {
        put("amountPaise", t.amount.paise)
        put("title", t.title)
        put("date", t.date.toString())
        put("notes", t.notes)
        put("createdBy", t.createdBy.value)
        put("createdAt", t.createdAt)
        put("updatedAt", t.updatedAt)
        put("schemaVersion", SCHEMA_VERSION)
        when (t) {
            is Transaction.Income -> {
                put("type", "income"); put("accountId", t.accountId.value)
                put("categoryId", t.categoryId.value); put("source", t.source)
            }
            is Transaction.Expense -> {
                put("type", "expense"); put("accountId", t.accountId.value)
                put("categoryId", t.categoryId.value); put("merchant", t.merchant)
            }
            is Transaction.Transfer -> {
                put("type", "transfer"); put("fromAccountId", t.fromAccountId.value)
                put("toAccountId", t.toAccountId.value)
            }
        }
    }

    fun decodeTransaction(id: String, d: Map<String, Any?>): Transaction {
        val txId = TransactionId(id)
        val amount = Money(d.long("amountPaise"))
        // Out-of-range values are refused here, so one bad record is skipped, never summed.
        require(amount.isPositive && amount.isWithinLimit) { "amount out of range" }
        val date = LocalDate.parse(d.string("date"))
        val title = d.string("title")
        val notes = d["notes"] as String?
        val createdBy = UserId(d.string("createdBy"))
        val createdAt = d.instant("createdAt")
        val updatedAt = d.instant("updatedAt")
        return when (val type = d.string("type")) {
            "income" -> Transaction.Income(
                txId, amount, title, date, AccountId(d.string("accountId")), CategoryId(d.string("categoryId")),
                d["source"] as String? ?: "", notes, createdBy, createdAt, updatedAt,
            )
            "expense" -> Transaction.Expense(
                txId, amount, title, date, AccountId(d.string("accountId")), CategoryId(d.string("categoryId")),
                d["merchant"] as String?, notes, createdBy, createdAt, updatedAt,
            )
            "transfer" -> Transaction.Transfer(
                txId, amount, title, date, AccountId(d.string("fromAccountId")), AccountId(d.string("toAccountId")),
                notes, createdBy, createdAt, updatedAt,
            )
            else -> error("Unknown transaction type: $type")
        }
    }

    // --- accounts ---

    fun encode(a: Account): Map<String, Any?> = mapOf(
        "name" to a.name,
        "type" to a.type.name,
        "openingBalancePaise" to a.openingBalance.paise,
        "openingBalanceDate" to a.openingBalanceDate.toString(),
        "isActive" to a.isActive,
        "currencyCode" to a.currencyCode,
        "createdAt" to a.createdAt,
        "updatedAt" to a.updatedAt,
        "schemaVersion" to SCHEMA_VERSION,
    )

    fun decodeAccount(id: String, d: Map<String, Any?>) = Account(
        id = AccountId(id),
        name = d.string("name"),
        type = runCatching { AccountType.valueOf(d.string("type")) }.getOrDefault(AccountType.OTHER),
        openingBalance = Money(d.long("openingBalancePaise")).also { require(it.isWithinLimit) { "opening balance out of range" } },
        openingBalanceDate = LocalDate.parse(d.string("openingBalanceDate")),
        isActive = d["isActive"] as Boolean? ?: true,
        createdAt = d.instant("createdAt"),
        updatedAt = d.instant("updatedAt"),
        currencyCode = d["currencyCode"] as String? ?: Account.INR,
    )

    // --- categories (only custom ones and changed built-ins are stored) ---

    fun encode(c: Category): Map<String, Any?> = mapOf(
        "name" to c.name,
        "kind" to if (c.kind == CategoryKind.INCOME) "income" else "expense",
        "isArchived" to c.isArchived,
        "systemKey" to c.systemKey,
        "isLoan" to c.isLoan,
        "schemaVersion" to SCHEMA_VERSION,
    )

    fun decodeCategory(id: String, d: Map<String, Any?>) = Category(
        id = CategoryId(id),
        name = d["name"] as String? ?: "",
        kind = if (d["kind"] == "income") CategoryKind.INCOME else CategoryKind.EXPENSE,
        isArchived = d["isArchived"] as Boolean? ?: false,
        systemKey = d["systemKey"] as String?,
        isLoan = d["isLoan"] as Boolean? ?: false,
    )

    // --- notes ---

    fun encode(n: NoteEntry): Map<String, Any?> = mapOf(
        "title" to n.title,
        "description" to n.description,
        "date" to n.date.toString(),
        "createdBy" to n.createdBy.value,
        "createdAt" to n.createdAt,
        "updatedAt" to n.updatedAt,
        "schemaVersion" to SCHEMA_VERSION,
    )

    fun decodeNote(id: String, d: Map<String, Any?>) = NoteEntry(
        id = NoteId(id),
        title = d.string("title"),
        description = d["description"] as String? ?: "",
        date = LocalDate.parse(d.string("date")),
        createdBy = UserId(d.string("createdBy")),
        createdAt = d.instant("createdAt"),
        updatedAt = d.instant("updatedAt"),
    )

    private fun Map<String, Any?>.string(key: String): String = this[key] as? String ?: error("Missing $key")
    private fun Map<String, Any?>.long(key: String): Long = (this[key] as? Number)?.toLong() ?: error("Missing $key")
    private fun Map<String, Any?>.instant(key: String): Instant = this[key] as? Instant ?: Instant.EPOCH
}
