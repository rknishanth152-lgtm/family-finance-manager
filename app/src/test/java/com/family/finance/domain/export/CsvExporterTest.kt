package com.family.finance.domain.export

import com.family.finance.domain.export.ExportSamples.FORMULA_NOTE
import com.family.finance.domain.export.ExportSamples.TAMIL_NOTE
import com.family.finance.domain.export.ExportSamples.TAMIL_TITLE
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CsvExporterTest {

    private val bytes = CsvExporter.export(ExportSamples.input())
    private val text = bytes.toString(Charsets.UTF_8)

    /** Minimal RFC 4180 reader, enough to read our own output back. */
    private fun parse(csv: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < csv.length) {
            val c = csv[i]
            when {
                quoted && c == '"' && csv.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> { row.add(field.toString()); field.clear() }
                !quoted && c == '\r' && csv.getOrNull(i + 1) == '\n' -> {
                    row.add(field.toString()); field.clear(); rows.add(row); row = mutableListOf(); i++
                }
                else -> field.append(c)
            }
            i++
        }
        return rows
    }

    private val rows = parse(text.removePrefix("\uFEFF"))

    @Test fun `starts with a UTF-8 byte order mark so Excel reads Tamil`() {
        assertThat(bytes.take(3).map { it.toInt() and 0xFF }).containsExactly(0xEF, 0xBB, 0xBF).inOrder()
    }

    @Test fun `header is in the chosen language and rows match the transactions`() {
        assertThat(rows.first().first()).isEqualTo("[ta] COL_DATE")
        assertThat(rows).hasSize(1 + 3)
        assertThat(rows.all { it.size == 11 }).isTrue()
    }

    @Test fun `Tamil text and notes with commas and quotes come back exactly as typed`() {
        val income = rows[1]
        assertThat(income[2]).isEqualTo(TAMIL_TITLE)
        assertThat(income[4]).isEqualTo("கூட்டுறவு சங்கம்")
        assertThat(income[9]).isEqualTo(TAMIL_NOTE)
    }

    @Test fun `amounts are exact plain numbers and dates are ISO`() {
        val income = rows[1]
        assertThat(income[0]).isEqualTo("2026-03-05")
        assertThat(income[6]).isEqualTo("1500.50")
        assertThat(income[7]).isEmpty()
        val transfer = rows[3]
        assertThat(transfer[8]).isEqualTo("1000.00")
        assertThat(transfer[6]).isEmpty()
        assertThat(transfer[7]).isEmpty()
    }

    @Test fun `text that a spreadsheet would run as a formula is neutralized`() {
        val expense = rows[2]
        assertThat(expense[9]).isEqualTo("'$FORMULA_NOTE")
        assertThat(expense[4]).isEqualTo("'+91 shop")
        for (start in listOf("=", "+", "-", "@", "\t", "\r")) {
            assertThat(CsvExporter.neutralize("${start}1+1")).startsWith("'")
        }
        assertThat(CsvExporter.neutralize("பால் = 10")).isEqualTo("பால் = 10")
    }

    @Test fun `amounts are numbers, never neutralized, even when negative`() {
        assertThat(CsvExporter.field(Cell.Amount(com.family.finance.domain.model.Money(-12_345)))).isEqualTo("-123.45")
    }

    @Test fun `text is always quoted, so it is never read as a number or date`() {
        assertThat(CsvExporter.field(Cell.Text("0012"))).isEqualTo("\"0012\"")
        assertThat(CsvExporter.field(Cell.Text("say \"hi\""))).isEqualTo("\"say \"\"hi\"\"\"")
    }

    @Test fun `lines end with CRLF`() {
        assertThat(text.removePrefix("\uFEFF").split("\r\n").filter { it.isNotEmpty() }).hasSize(4)
    }
}
