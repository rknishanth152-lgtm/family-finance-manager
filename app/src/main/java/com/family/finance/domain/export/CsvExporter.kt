package com.family.finance.domain.export

import com.family.finance.domain.model.MoneyFormatter
import java.time.format.DateTimeFormatter

/**
 * UTF-8 CSV of the report's transactions (PRD 12.2). Starts with a byte-order mark so
 * Excel opens Tamil text correctly, and uses CRLF line endings as RFC 4180 expects.
 */
object CsvExporter {

    private const val BOM = "\uFEFF"
    private const val EOL = "\r\n"

    fun export(input: StatementInput): ByteArray {
        val l = input.labels
        val text = buildString {
            append(BOM)
            appendRow(StatementTables.transactionHeader(l).map { Cell.Text(it) })
            input.report.transactions.forEach { appendRow(StatementTables.transactionRow(it, l)) }
        }
        return text.toByteArray(Charsets.UTF_8)
    }

    private fun StringBuilder.appendRow(cells: List<Cell>) {
        cells.joinTo(this, ",") { field(it) }
        append(EOL)
    }

    /** Amounts are plain numbers (1250.50) and dates ISO (2026-11-01), so spreadsheets read them as values. */
    fun field(cell: Cell): String = when (cell) {
        is Cell.Text -> quote(neutralize(cell.value))
        is Cell.Amount -> MoneyFormatter.plain(cell.money)
        is Cell.Day -> DateTimeFormatter.ISO_LOCAL_DATE.format(cell.date)
        is Cell.Count -> cell.value.toString()
        is Cell.Percent -> percentText(cell.basisPoints)
        Cell.Empty -> ""
    }

    /**
     * Formula-injection guard (OWASP "CSV injection"): text that a spreadsheet would run as
     * a formula, such as a note starting with "=", gets a leading apostrophe so it stays text.
     */
    fun neutralize(value: String): String =
        if (value.isNotEmpty() && value[0] in FORMULA_STARTS) "'$value" else value

    private val FORMULA_STARTS = charArrayOf('=', '+', '-', '@', '\t', '\r')

    private fun quote(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${value.replace("\"", "\"\"")}\"" else value
}
