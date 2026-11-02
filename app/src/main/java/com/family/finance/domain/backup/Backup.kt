package com.family.finance.domain.backup

import com.family.finance.domain.calc.IncomeExpenseTotals
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
 * A full backup of one family's records (PRD 12.5). The app has no budgets, recurrences
 * or attachments yet, so there is nothing of those to include.
 */
data class Backup(
    val createdAt: Instant,
    val familyName: String,
    val accounts: List<Account>,
    val categories: List<Category>,
    val transactions: List<Transaction>,
    val notes: List<NoteEntry>,
) {
    val totals: IncomeExpenseTotals get() = IncomeExpenseTotals.of(transactions)
}

/** Why a file cannot be restored (PRD 12.7: identify the schema, detect malformed files). */
enum class BackupProblem {
    /** Not readable as text/JSON at all (wrong file, or damaged). */
    UNREADABLE,
    /** Valid JSON but not a backup made by this app. */
    NOT_A_BACKUP,
    /** Made by a newer version of the app; update the app first. */
    NEWER_VERSION,
    /** A record is missing a field or has a wrong value. */
    BAD_RECORD,
    /** The same ID appears twice. */
    DUPLICATE_ID,
    /** The totals written in the file do not match its records: the file was cut short or edited. */
    TOTALS_MISMATCH,
}

sealed interface BackupRead {
    data class Ok(val backup: Backup) : BackupRead
    data class Invalid(val problem: BackupProblem, val detail: String) : BackupRead
}

/**
 * Backup file format, version [FORMAT_VERSION]. Its own format (not the cloud's field
 * layout), so backups stay readable whatever changes in the cloud storage later. Money
 * is whole paise and dates are "YYYY-MM-DD" text, as everywhere else (PRD 8.3, 8.5).
 */
object BackupCodec {

    const val FORMAT = "family-finance-backup"
    const val FORMAT_VERSION = 1L
    const val FILE_EXTENSION = "ffbackup"
    const val MIME_TYPE = "application/json"

    fun encode(b: Backup): String = Json.write(
        linkedMapOf(
            "format" to FORMAT,
            "formatVersion" to FORMAT_VERSION,
            "createdAt" to b.createdAt.toString(),
            "familyName" to b.familyName,
            "accounts" to b.accounts.map(::account),
            "categories" to b.categories.map(::category),
            "transactions" to b.transactions.map(::transaction),
            "notes" to b.notes.map(::note),
            // Written last and checked on reading, so a file cut short is detected.
            "totals" to linkedMapOf(
                "transactionCount" to b.transactions.size.toLong(),
                "incomePaise" to b.totals.income.paise,
                "expensePaise" to b.totals.expense.paise,
            ),
        ),
    )

    fun decode(text: String): BackupRead {
        val root = try {
            Json.parse(text.removePrefix("\uFEFF"))
        } catch (e: Json.ParseException) {
            return BackupRead.Invalid(BackupProblem.UNREADABLE, e.message.orEmpty())
        }
        if (root !is Map<*, *> || root["format"] != FORMAT) return BackupRead.Invalid(BackupProblem.NOT_A_BACKUP, "format")
        val version = root["formatVersion"] as? Long ?: return BackupRead.Invalid(BackupProblem.NOT_A_BACKUP, "formatVersion")
        if (version > FORMAT_VERSION) return BackupRead.Invalid(BackupProblem.NEWER_VERSION, "version $version")

        val backup = try {
            Backup(
                createdAt = Instant.parse(root.str("createdAt")),
                familyName = root.str("familyName"),
                accounts = root.list("accounts").map(::readAccount),
                categories = root.list("categories").map(::readCategory),
                transactions = root.list("transactions").map(::readTransaction),
                notes = root.list("notes").map(::readNote),
            )
        } catch (e: RuntimeException) {
            // Any missing field, wrong type, bad date or bad amount: nothing is restored.
            return BackupRead.Invalid(BackupProblem.BAD_RECORD, e.message ?: e::class.simpleName.orEmpty())
        }

        duplicate(backup.accounts.map { "a:" + it.id.value } + backup.categories.map { "c:" + it.id.value } +
            backup.transactions.map { "t:" + it.id.value } + backup.notes.map { "n:" + it.id.value })
            ?.let { return BackupRead.Invalid(BackupProblem.DUPLICATE_ID, it) }

        val totals = root["totals"] as? Map<*, *> ?: return BackupRead.Invalid(BackupProblem.TOTALS_MISMATCH, "no totals")
        if (totals["transactionCount"] != backup.transactions.size.toLong() ||
            totals["incomePaise"] != backup.totals.income.paise ||
            totals["expensePaise"] != backup.totals.expense.paise
        ) {
            return BackupRead.Invalid(BackupProblem.TOTALS_MISMATCH, "totals differ from records")
        }
        return BackupRead.Ok(backup)
    }

    private fun duplicate(ids: List<String>): String? {
        val seen = HashSet<String>()
        return ids.firstOrNull { !seen.add(it) }
    }

    // --- writing ---

    private fun account(a: Account) = linkedMapOf(
        "id" to a.id.value, "name" to a.name, "type" to a.type.name,
        "openingBalancePaise" to a.openingBalance.paise, "openingBalanceDate" to a.openingBalanceDate.toString(),
        "isActive" to a.isActive, "currencyCode" to a.currencyCode,
        "createdAt" to a.createdAt.toString(), "updatedAt" to a.updatedAt.toString(),
    )

    private fun category(c: Category) = linkedMapOf(
        "id" to c.id.value, "name" to c.name, "kind" to c.kind.name, "isArchived" to c.isArchived,
        "systemKey" to c.systemKey, "isLoan" to c.isLoan,
    )

    private fun transaction(t: Transaction): Map<String, Any?> {
        val common = linkedMapOf<String, Any?>(
            "id" to t.id.value, "amountPaise" to t.amount.paise, "title" to t.title, "date" to t.date.toString(),
            "notes" to t.notes, "createdBy" to t.createdBy.value,
            "createdAt" to t.createdAt.toString(), "updatedAt" to t.updatedAt.toString(),
        )
        when (t) {
            is Transaction.Income -> common += linkedMapOf("type" to "income", "accountId" to t.accountId.value, "categoryId" to t.categoryId.value, "source" to t.source)
            is Transaction.Expense -> common += linkedMapOf("type" to "expense", "accountId" to t.accountId.value, "categoryId" to t.categoryId.value, "merchant" to t.merchant)
            is Transaction.Transfer -> common += linkedMapOf("type" to "transfer", "fromAccountId" to t.fromAccountId.value, "toAccountId" to t.toAccountId.value)
        }
        return common
    }

    private fun note(n: NoteEntry) = linkedMapOf(
        "id" to n.id.value, "title" to n.title, "description" to n.description, "date" to n.date.toString(),
        "createdBy" to n.createdBy.value, "createdAt" to n.createdAt.toString(), "updatedAt" to n.updatedAt.toString(),
    )

    // --- reading (every field checked; any problem rejects the whole file) ---

    private fun readAccount(o: Any?): Account {
        val m = o.obj("account")
        return Account(
            id = AccountId(m.id("id")),
            name = m.str("name").also { require(it.isNotBlank()) { "account name is blank" } },
            type = AccountType.valueOf(m.str("type")),
            openingBalance = Money(m.long("openingBalancePaise")).also { require(it.isWithinLimit) { "opening balance out of range" } },
            openingBalanceDate = m.date("openingBalanceDate"),
            isActive = m.bool("isActive"),
            createdAt = Instant.parse(m.str("createdAt")),
            updatedAt = Instant.parse(m.str("updatedAt")),
            currencyCode = m.str("currencyCode"),
        )
    }

    private fun readCategory(o: Any?): Category {
        val m = o.obj("category")
        return Category(
            id = CategoryId(m.id("id")),
            name = m.str("name"),
            kind = CategoryKind.valueOf(m.str("kind")),
            isArchived = m.bool("isArchived"),
            systemKey = m.optStr("systemKey"),
            isLoan = m.bool("isLoan"),
        )
    }

    private fun readTransaction(o: Any?): Transaction {
        val m = o.obj("transaction")
        val id = TransactionId(m.id("id"))
        val amount = Money(m.long("amountPaise")).also { require(it.isPositive && it.isWithinLimit) { "amount out of range in ${id.value}" } }
        val title = m.str("title")
        val date = m.date("date")
        val notes = m.optStr("notes")
        val createdBy = UserId(m.str("createdBy"))
        val createdAt = Instant.parse(m.str("createdAt"))
        val updatedAt = Instant.parse(m.str("updatedAt"))
        return when (val type = m.str("type")) {
            "income" -> Transaction.Income(id, amount, title, date, AccountId(m.id("accountId")), CategoryId(m.id("categoryId")), m.str("source"), notes, createdBy, createdAt, updatedAt)
            "expense" -> Transaction.Expense(id, amount, title, date, AccountId(m.id("accountId")), CategoryId(m.id("categoryId")), m.optStr("merchant"), notes, createdBy, createdAt, updatedAt)
            "transfer" -> Transaction.Transfer(id, amount, title, date, AccountId(m.id("fromAccountId")), AccountId(m.id("toAccountId")), notes, createdBy, createdAt, updatedAt)
            else -> throw IllegalArgumentException("unknown transaction type $type")
        }
    }

    private fun readNote(o: Any?): NoteEntry {
        val m = o.obj("note")
        return NoteEntry(
            id = NoteId(m.id("id")), title = m.str("title").also { require(it.isNotBlank()) { "note title is blank" } }, description = m.str("description"),
            date = m.date("date"), createdBy = UserId(m.str("createdBy")),
            createdAt = Instant.parse(m.str("createdAt")), updatedAt = Instant.parse(m.str("updatedAt")),
        )
    }

    /**
     * A record ID becomes one Firestore document name, so it must be a single safe path
     * segment: not empty, no '/', not '.' or '..', and not a reserved '__name__' form.
     */
    private fun Map<*, *>.id(key: String): String = str(key).also {
        require(it.isNotEmpty() && it.length <= 200 && '/' !in it && it != "." && it != ".." && !(it.startsWith("__") && it.endsWith("__"))) {
            "unsafe id in \"$key\""
        }
    }

    /** Dates as YYYY-MM-DD within years 1..9999, the form the cloud rules accept. */
    private fun Map<*, *>.date(key: String): LocalDate = LocalDate.parse(str(key)).also {
        require(it.year in 1..9999) { "date out of range in \"$key\"" }
    }

    private fun Any?.obj(what: String): Map<*, *> = this as? Map<*, *> ?: throw IllegalArgumentException("$what is not an object")
    private fun Map<*, *>.str(key: String): String = this[key] as? String ?: throw IllegalArgumentException("missing text \"$key\"")
    private fun Map<*, *>.optStr(key: String): String? = when (val v = this[key]) {
        null -> null
        is String -> v
        else -> throw IllegalArgumentException("\"$key\" is not text")
    }
    private fun Map<*, *>.long(key: String): Long = this[key] as? Long ?: throw IllegalArgumentException("missing number \"$key\"")
    private fun Map<*, *>.bool(key: String): Boolean = this[key] as? Boolean ?: throw IllegalArgumentException("missing true/false \"$key\"")
    private fun Map<*, *>.list(key: String): List<*> = this[key] as? List<*> ?: throw IllegalArgumentException("missing list \"$key\"")
}
