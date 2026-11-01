package com.family.finance.domain.export

import com.family.finance.domain.Fixtures.date
import com.family.finance.domain.export.ExportSamples.FORMULA_NOTE
import com.family.finance.domain.export.ExportSamples.TAMIL_NOTE
import com.family.finance.domain.export.ExportSamples.TAMIL_TITLE
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class XlsxExporterTest {

    private val parts: Map<String, String> = unzip(XlsxExporter.export(ExportSamples.input()))

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val out = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { out[it.name] = zip.readBytes().toString(Charsets.UTF_8) }
        }
        return out
    }

    private fun xml(part: String): Element = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(ByteArrayInputStream(parts.getValue(part).toByteArray())).documentElement

    /** Cell reference -> (text or value). */
    private fun cells(sheet: Int): Map<String, String> {
        val nodes = xml("xl/worksheets/sheet$sheet.xml").getElementsByTagNameNS("*", "c")
        return (0 until nodes.length).associate { i ->
            val c = nodes.item(i) as Element
            val text = c.getElementsByTagNameNS("*", "t").item(0)?.textContent
            val value = c.getElementsByTagNameNS("*", "v").item(0)?.textContent
            c.getAttribute("r") to (text ?: value ?: "")
        }
    }

    @Test fun `package has every required part and all of them are well-formed XML`() {
        assertThat(parts.keys).containsAtLeast(
            "[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/styles.xml",
            "xl/worksheets/sheet1.xml", "xl/worksheets/sheet4.xml",
        )
        parts.keys.forEach { xml(it) }
    }

    @Test fun `four sheets named in the chosen language`() {
        val sheets = xml("xl/workbook.xml").getElementsByTagNameNS("*", "sheet")
        val names = (0 until sheets.length).map { (sheets.item(it) as Element).getAttribute("name") }
        // "[" and "]" are not allowed in sheet names, so the fake labels lose them.
        assertThat(names).containsExactly("ta SHEET_SUMMARY", "ta SHEET_TRANSACTIONS", "ta SHEET_CATEGORIES", "ta SHEET_ACCOUNTS").inOrder()
    }

    @Test fun `Tamil text is kept exactly`() {
        val t = cells(2)
        assertThat(t["C2"]).isEqualTo(TAMIL_TITLE)
        assertThat(t["J2"]).isEqualTo(TAMIL_NOTE)
        assertThat(cells(1)["B2"]).isEqualTo("குடும்பம்")
    }

    @Test fun `dates and amounts are real values`() {
        val t = cells(2)
        assertThat(t["A2"]).isEqualTo(XlsxWriter.excelSerial(date("2026-03-05")).toString())
        assertThat(t["G2"]).isEqualTo("1500.50")
        assertThat(XlsxWriter.excelSerial(date("1900-03-01"))).isEqualTo(61)
        assertThat(XlsxWriter.excelSerial(date("2026-11-01"))).isEqualTo(46327)
    }

    @Test fun `formula-like text stays text and no formulas are written`() {
        assertThat(cells(2)["J3"]).isEqualTo(FORMULA_NOTE)
        parts.filterKeys { it.startsWith("xl/worksheets/") }.values.forEach { sheet ->
            assertThat(sheet).doesNotContain("<f>")
            assertThat(sheet).doesNotContain("<f ")
        }
        val c = xml("xl/worksheets/sheet2.xml").getElementsByTagNameNS("*", "c")
        val j3 = (0 until c.length).map { c.item(it) as Element }.single { it.getAttribute("r") == "J3" }
        assertThat(j3.getAttribute("t")).isEqualTo("inlineStr")
    }

    @Test fun `summary totals equal the report`() {
        val summary = cells(1).values.toList()
        assertThat(summary).contains("1500.50")
        assertThat(summary).contains("400.00")
        assertThat(summary).contains("1100.50")
    }

    @Test fun `category sheet shares add to 100 percent per kind`() {
        val rows = cells(4 - 1)
        assertThat(rows["D2"]).isEqualTo("100.00")
    }

    @Test fun `escape removes characters XML cannot hold and escapes specials`() {
        assertThat(XlsxWriter.escape("a<b>&\"c\u0001")).isEqualTo("a&lt;b&gt;&amp;&quot;c")
        assertThat(XlsxWriter.escape("தமிழ் 😀")).isEqualTo("தமிழ் 😀")
    }

    @Test fun `column names and sheet names follow Excel rules`() {
        assertThat(listOf(0, 25, 26, 27, 701, 702).map(XlsxWriter::columnName)).containsExactly("A", "Z", "AA", "AB", "ZZ", "AAA").inOrder()
        assertThat(XlsxWriter.sheetName("a/b:c[d]")).isEqualTo("abcd")
        assertThat(XlsxWriter.sheetName("x".repeat(40))).hasLength(31)
        assertThat(XlsxWriter.sheetName("")).isEqualTo("Sheet")
    }
}
