package com.family.finance.domain.export

import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.CategoryId
import com.family.finance.domain.model.Money
import com.family.finance.domain.model.Transaction
import com.family.finance.domain.model.UserId
import com.family.finance.domain.report.CategoryTotal
import com.family.finance.domain.report.Report
import com.family.finance.domain.report.Reports
import com.family.finance.domain.usecase.TypeFilter
import java.time.LocalDate

/** Fixed export wording, supplied in the export's own language (independent of the app's). */
enum class ExportText {
    STATEMENT_TITLE, FAMILY, PERIOD, GENERATED_ON, FILTERS, ALL_RECORDS,
    OPENING, OPENING_ADDED, INCOME, EXPENSE, BALANCE, TRANSFERS_IN, TRANSFERS_OUT, CLOSING,
    LOAN_RECEIVED_INCLUDED, LOAN_REPAID_INCLUDED, BALANCES_NOT_SHOWN,
    COL_DATE, COL_TYPE, COL_TITLE, COL_CATEGORY, COL_PARTY, COL_ACCOUNT,
    COL_INCOME, COL_EXPENSE, COL_TRANSFER, COL_NOTES, COL_CREATED_BY,
    COL_AMOUNT, COL_SHARE, COL_COUNT, COL_SOURCE, COL_KIND,
    COL_OPENING, COL_MONEY_IN, COL_MONEY_OUT, COL_CLOSING,
    SHEET_SUMMARY, SHEET_TRANSACTIONS, SHEET_CATEGORIES, SHEET_ACCOUNTS,
    SECTION_SUMMARY, SECTION_CHART, SECTION_EXPENSE_CATEGORIES, SECTION_INCOME_CATEGORIES,
    SECTION_SOURCES, SECTION_ACCOUNTS, SECTION_TRANSACTIONS,
    TYPE_INCOME, TYPE_EXPENSE, TYPE_TRANSFER, NOT_GIVEN, NO_TRANSACTIONS,
}

/** Everything an export needs to turn records into words, in one language. */
interface ExportLabels {
    operator fun get(text: ExportText): String
    fun category(id: CategoryId): String
    fun account(id: AccountId): String
    fun person(id: UserId): String
    /** Compact date for table cells. */
    fun date(date: LocalDate): String
    /** Date written out in words, for headings. */
    fun longDate(date: LocalDate): String
    fun page(number: Int, of: Int): String
}

/** One export request: the report plus the details printed around it. */
data class StatementInput(
    val report: Report,
    val familyName: String,
    val generatedOn: LocalDate,
    val labels: ExportLabels,
)

/** A spreadsheet-style value. Amounts and dates stay typed so spreadsheets can add and sort them. */
sealed interface Cell {
    data class Text(val value: String, val bold: Boolean = false) : Cell
    data class Amount(val money: Money, val bold: Boolean = false) : Cell
    data class Day(val date: LocalDate) : Cell
    data class Count(val value: Long) : Cell
    /** Hundredths of a percent, e.g. 1250 = 12.50%. */
    data class Percent(val basisPoints: Long) : Cell {
        init {
            require(basisPoints >= 0) { "Shares are never negative" }
        }
    }
    data object Empty : Cell
}

/** "12.50" for 1250 basis points; always ASCII digits whatever the phone's language. */
fun percentText(basisPoints: Long): String = "${basisPoints / 100}.${(basisPoints % 100).toString().padStart(2, '0')}"

/** The tables shared by the CSV, Excel and PDF exports, so all three show the same figures. */
object StatementTables {

    fun transactionHeader(l: ExportLabels): List<String> = listOf(
        l[ExportText.COL_DATE], l[ExportText.COL_TYPE], l[ExportText.COL_TITLE], l[ExportText.COL_CATEGORY],
        l[ExportText.COL_PARTY], l[ExportText.COL_ACCOUNT], l[ExportText.COL_INCOME], l[ExportText.COL_EXPENSE],
        l[ExportText.COL_TRANSFER], l[ExportText.COL_NOTES], l[ExportText.COL_CREATED_BY],
    )

    /** User-typed text (title, source, merchant, notes) is passed through exactly as typed. */
    fun transactionRow(t: Transaction, l: ExportLabels): List<Cell> {
        val notes = t.notes?.let { Cell.Text(it) } ?: Cell.Empty
        val createdBy = Cell.Text(l.person(t.createdBy))
        return when (t) {
            is Transaction.Income -> listOf(
                Cell.Day(t.date), Cell.Text(l[ExportText.TYPE_INCOME]), Cell.Text(t.title),
                Cell.Text(l.category(t.categoryId)), textOrEmpty(t.source), Cell.Text(l.account(t.accountId)),
                Cell.Amount(t.amount), Cell.Empty, Cell.Empty, notes, createdBy,
            )
            is Transaction.Expense -> listOf(
                Cell.Day(t.date), Cell.Text(l[ExportText.TYPE_EXPENSE]), Cell.Text(t.title),
                Cell.Text(l.category(t.categoryId)), textOrEmpty(t.merchant), Cell.Text(l.account(t.accountId)),
                Cell.Empty, Cell.Amount(t.amount), Cell.Empty, notes, createdBy,
            )
            is Transaction.Transfer -> listOf(
                Cell.Day(t.date), Cell.Text(l[ExportText.TYPE_TRANSFER]), Cell.Text(t.title),
                Cell.Empty, Cell.Empty, Cell.Text("${l.account(t.fromAccountId)} → ${l.account(t.toAccountId)}"),
                Cell.Empty, Cell.Empty, Cell.Amount(t.amount), notes, createdBy,
            )
        }
    }

    /** Label/value lines of the statement summary (PRD 12.1). */
    fun summaryLines(input: StatementInput): List<Pair<String, Money>> {
        val l = input.labels
        val r = input.report
        return buildList {
            r.balances?.let { b ->
                add(l[ExportText.OPENING] to b.opening)
                if (!b.openingAdded.isZero) add(l[ExportText.OPENING_ADDED] to b.openingAdded)
            }
            add(l[ExportText.INCOME] to r.totals.income)
            add(l[ExportText.EXPENSE] to r.totals.expense)
            add(l[ExportText.BALANCE] to r.totals.balance)
            r.balances?.let { b ->
                if (!b.transfersIn.isZero) add(l[ExportText.TRANSFERS_IN] to b.transfersIn)
                if (!b.transfersOut.isZero) add(l[ExportText.TRANSFERS_OUT] to b.transfersOut)
                add(l[ExportText.CLOSING] to b.closing)
            }
            if (!r.loanReceived.isZero) add(l[ExportText.LOAN_RECEIVED_INCLUDED] to r.loanReceived)
            if (!r.loanRepaid.isZero) add(l[ExportText.LOAN_REPAID_INCLUDED] to r.loanRepaid)
        }
    }

    /** "Account: Cash · Type: Expense", or "All records" when nothing is filtered. */
    fun filterText(input: StatementInput): String {
        val l = input.labels
        val f = input.report.filter
        val parts = listOfNotNull(
            f.accountId?.let { "${l[ExportText.COL_ACCOUNT]}: ${l.account(it)}" },
            f.categoryId?.let { "${l[ExportText.COL_CATEGORY]}: ${l.category(it)}" },
            when (f.type) {
                TypeFilter.ALL -> null
                TypeFilter.INCOME -> l[ExportText.TYPE_INCOME]
                TypeFilter.EXPENSE -> l[ExportText.TYPE_EXPENSE]
                TypeFilter.TRANSFER -> l[ExportText.TYPE_TRANSFER]
            }?.let { "${l[ExportText.COL_TYPE]}: $it" },
        )
        return if (parts.isEmpty()) l[ExportText.ALL_RECORDS] else parts.joinToString(" · ")
    }

    fun periodText(input: StatementInput): String {
        val range = input.report.filter.range
        return "${input.labels.longDate(range.start)} – ${input.labels.longDate(range.end)}"
    }

    fun categoryRows(totals: List<CategoryTotal>, whole: Money, l: ExportLabels): List<List<Cell>> =
        totals.map {
            listOf(
                Cell.Text(l.category(it.categoryId)), Cell.Amount(it.total),
                Cell.Percent(Reports.shareBasisPoints(it.total, whole)), Cell.Count(it.count.toLong()),
            )
        }

    fun sourceRows(input: StatementInput): List<List<Cell>> = input.report.incomeBySource.map {
        listOf(
            Cell.Text(it.source ?: input.labels[ExportText.NOT_GIVEN]), Cell.Amount(it.total),
            Cell.Percent(Reports.shareBasisPoints(it.total, input.report.totals.income)), Cell.Count(it.count.toLong()),
        )
    }

    fun accountHeader(l: ExportLabels) = listOf(
        l[ExportText.COL_ACCOUNT], l[ExportText.COL_OPENING], l[ExportText.COL_MONEY_IN],
        l[ExportText.COL_MONEY_OUT], l[ExportText.COL_CLOSING],
    )

    /** Money in = income + transfers in + opening balance added in the period; money out = expense + transfers out. */
    fun accountRows(input: StatementInput): List<List<Cell>> = input.report.accounts.map {
        listOf(
            Cell.Text(it.account.name), Cell.Amount(it.opening),
            Cell.Amount(it.income + it.transfersIn + it.openingAdded), Cell.Amount(it.expense + it.transfersOut),
            Cell.Amount(it.closing),
        )
    }

    private fun textOrEmpty(value: String?): Cell = if (value.isNullOrBlank()) Cell.Empty else Cell.Text(value)
}
