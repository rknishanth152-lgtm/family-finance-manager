package com.family.finance.domain.legacy

import com.family.finance.domain.backup.Backup
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
import com.family.finance.domain.model.sumOfMoney
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
 * Import from the family's previous app ("Income Expense", a Room/SQLite backup file),
 * PRD 13. The reader (data layer) turns the file into these plain rows; this file decides
 * what each row becomes and reports everything it did (PRD 13.3).
 */

/** One row of the old app's main table. Amounts are already whole paise. */
data class LegacyEntry(
    val id: Long,
    /** When the entry was dated, in milliseconds (the old app stored a time of day too). */
    val epochMillis: Long,
    val incomePaise: Long,
    val expensePaise: Long,
    /** False when an amount had more than two decimals and could not be converted exactly. */
    val exactAmounts: Boolean,
    val notes: String?,
    val category: String?,
    val paymentMethod: String?,
    val hasBill: Boolean,
    val isDeleted: Boolean,
)

data class LegacyCategory(val id: Long, val name: String, val isIncome: Boolean)
data class LegacyPaymentMethod(val id: Long, val name: String)
data class LegacyNote(val id: Long, val epochMillis: Long, val text: String?)

data class LegacySource(
    val entries: List<LegacyEntry>,
    val categories: List<LegacyCategory>,
    val paymentMethods: List<LegacyPaymentMethod>,
    val notes: List<LegacyNote>,
)

enum class RejectReason { NO_AMOUNT, BOTH_AMOUNTS, NOT_EXACT_PAISE, AMOUNT_TOO_LARGE, UNKNOWN_CATEGORY, UNKNOWN_PAYMENT_METHOD, CATEGORY_KIND_MISMATCH }

data class CategoryMapping(val oldName: String, val newId: CategoryId, val kind: CategoryKind, val entries: Int)
data class AccountMapping(val oldName: String, val newId: AccountId, val entries: Int, val income: Money, val expense: Money)

/** Everything the migration report must show (PRD 13.3). Totals cover imported entries only. */
data class ImportReport(
    val totalRows: Int,
    val deletedSkipped: Int,
    val imported: Int,
    val rejected: Map<RejectReason, List<Long>>,
    /** Entries without a description; their title is the category name. */
    val untitled: Int,
    /** Entries that had receipt photos in the old app; the photos are not in the backup file. */
    val withBills: Int,
    /** Groups of entries with the same date, type, amount and title (still imported, listed for checking). */
    val possibleDuplicateGroups: Int,
    val categoryMappings: List<CategoryMapping>,
    val unusedCategories: Int,
    val accounts: List<AccountMapping>,
    val incomeTotal: Money,
    val expenseTotal: Money,
    val firstDate: LocalDate?,
    val lastDate: LocalDate?,
    val notesImported: Int,
) {
    val rejectedCount: Int get() = rejected.values.sumOf { it.size }
    val net: Money get() = incomeTotal - expenseTotal
}

data class LegacyImportPlan(val records: Backup, val report: ImportReport)

object LegacyImport {

    /** The old app stored dates as moments; India time gives back the day the person picked. */
    val INDIA: ZoneId = ZoneId.of("Asia/Kolkata")

    fun accountId(m: LegacyPaymentMethod) = AccountId("legacy-pm-${m.id}")
    fun categoryId(c: LegacyCategory) = CategoryId("legacy-cat-${c.id}")
    fun transactionId(e: LegacyEntry) = TransactionId("legacy-tx-${e.id}")
    fun noteId(n: LegacyNote) = NoteId("legacy-note-${n.id}")

    fun plan(source: LegacySource, me: UserId, now: Instant, familyName: String, zone: ZoneId = INDIA): LegacyImportPlan {
        val categoriesByName = source.categories.associateBy { it.name }
        val methodsByName = source.paymentMethods.associateBy { it.name }
        val live = source.entries.filter { !it.isDeleted }

        val rejected = linkedMapOf<RejectReason, MutableList<Long>>()
        fun reject(e: LegacyEntry, r: RejectReason) = rejected.getOrPut(r) { mutableListOf() }.add(e.id)

        data class Accepted(val entry: LegacyEntry, val category: LegacyCategory, val method: LegacyPaymentMethod)
        val accepted = live.mapNotNull { e ->
            val category = e.category?.let(categoriesByName::get)
            val method = e.paymentMethod?.let(methodsByName::get)
            when {
                !e.exactAmounts -> reject(e, RejectReason.NOT_EXACT_PAISE)
                e.incomePaise > Money.MAX_AMOUNT_PAISE || e.expensePaise > Money.MAX_AMOUNT_PAISE -> reject(e, RejectReason.AMOUNT_TOO_LARGE)
                e.incomePaise > 0 && e.expensePaise > 0 -> reject(e, RejectReason.BOTH_AMOUNTS)
                e.incomePaise <= 0 && e.expensePaise <= 0 -> reject(e, RejectReason.NO_AMOUNT)
                category == null -> reject(e, RejectReason.UNKNOWN_CATEGORY)
                method == null -> reject(e, RejectReason.UNKNOWN_PAYMENT_METHOD)
                category.isIncome != (e.incomePaise > 0) -> reject(e, RejectReason.CATEGORY_KIND_MISMATCH)
                else -> return@mapNotNull Accepted(e, category, method)
            }
            null
        }

        fun day(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val firstDate = accepted.minOfOrNull { day(it.entry.epochMillis) }
        val lastDate = accepted.maxOfOrNull { day(it.entry.epochMillis) }

        val usedCategories = accepted.map { it.category }.distinct()
        val usedMethods = accepted.map { it.method }.distinct()

        // The old app had no opening balances: each account starts at zero before the first entry.
        val accounts = usedMethods.map { m ->
            Account(
                id = accountId(m), name = m.name.trim().ifEmpty { m.id.toString() },
                type = if (m.name.trim().equals("cash", ignoreCase = true)) AccountType.CASH else AccountType.OTHER,
                openingBalance = Money.ZERO, openingBalanceDate = firstDate ?: day(now.toEpochMilli()),
                isActive = true, createdAt = now, updatedAt = now,
            )
        }
        // The father's own category names, kept exactly (decided 2026-11-01).
        val categories = usedCategories.map { c ->
            Category(id = categoryId(c), name = c.name, kind = if (c.isIncome) CategoryKind.INCOME else CategoryKind.EXPENSE, isArchived = false)
        }

        val transactions = accepted.map { (e, category, method) ->
            val title = e.notes?.takeIf { it.isNotBlank() } ?: category.name
            val at = Instant.ofEpochMilli(e.epochMillis)
            if (e.incomePaise > 0) {
                // The old app had no "received from"; left empty and shown as "Not given".
                Transaction.Income(transactionId(e), Money(e.incomePaise), title, day(e.epochMillis), accountId(method), categoryId(category), "", null, me, at, at)
            } else {
                Transaction.Expense(transactionId(e), Money(e.expensePaise), title, day(e.epochMillis), accountId(method), categoryId(category), null, null, me, at, at)
            }
        }

        val notes = source.notes.filter { !it.text.isNullOrBlank() }.map { n ->
            val at = Instant.ofEpochMilli(n.epochMillis)
            NoteEntry(noteId(n), n.text!!, "", day(n.epochMillis), me, at, at)
        }

        val duplicateGroups = transactions.groupBy { listOf(it.date, it::class, it.amount, it.title) }.count { it.value.size > 1 }

        val report = ImportReport(
            totalRows = source.entries.size,
            deletedSkipped = source.entries.count { it.isDeleted },
            imported = transactions.size,
            rejected = rejected,
            untitled = accepted.count { it.entry.notes.isNullOrBlank() },
            withBills = accepted.count { it.entry.hasBill },
            possibleDuplicateGroups = duplicateGroups,
            categoryMappings = usedCategories.map { c ->
                CategoryMapping(c.name, categoryId(c), if (c.isIncome) CategoryKind.INCOME else CategoryKind.EXPENSE, accepted.count { it.category == c })
            }.sortedByDescending { it.entries },
            unusedCategories = source.categories.size - usedCategories.size,
            accounts = usedMethods.map { m ->
                val mine = transactions.filter { (it as? Transaction.Income)?.accountId == accountId(m) || (it as? Transaction.Expense)?.accountId == accountId(m) }
                AccountMapping(
                    m.name, accountId(m), mine.size,
                    mine.filterIsInstance<Transaction.Income>().sumOfMoney { it.amount },
                    mine.filterIsInstance<Transaction.Expense>().sumOfMoney { it.amount },
                )
            },
            incomeTotal = transactions.filterIsInstance<Transaction.Income>().sumOfMoney { it.amount },
            expenseTotal = transactions.filterIsInstance<Transaction.Expense>().sumOfMoney { it.amount },
            firstDate = firstDate,
            lastDate = lastDate,
            notesImported = notes.size,
        )
        return LegacyImportPlan(Backup(now, familyName, accounts, categories, transactions, notes), report)
    }
}
