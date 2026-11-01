package com.family.finance.domain.export

import com.family.finance.domain.model.MoneyFormatter
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A minimal Office Open XML (.xlsx) writer: an xlsx file is a zip of XML parts. Only what
 * the statement needs: inline text, numbers, dates, bold, column widths, a frozen header.
 *
 * Text is always written as an inline string, which a spreadsheet never evaluates, so a
 * note starting with "=" cannot become a formula (PRD 12.2). No formulas are written.
 */
object XlsxWriter {

    data class Sheet(
        val name: String,
        val rows: List<List<Cell>>,
        /** Width of each column in characters. */
        val columnWidths: List<Int> = emptyList(),
        val freezeFirstRow: Boolean = false,
    )

    // Indexes into cellXfs in styles.xml.
    private const val STYLE_TEXT = 0
    private const val STYLE_BOLD = 1
    private const val STYLE_DATE = 2
    private const val STYLE_AMOUNT = 3
    private const val STYLE_PERCENT = 4
    private const val STYLE_COUNT = 5
    private const val STYLE_AMOUNT_BOLD = 6

    /** Excel's day 0 for the 1900 date system (the 1900 leap-year quirk only affects dates before March 1900). */
    private val EXCEL_EPOCH: LocalDate = LocalDate.of(1899, 12, 30)

    fun write(sheets: List<Sheet>): ByteArray {
        require(sheets.isNotEmpty()) { "A workbook needs at least one sheet" }
        val names = uniqueNames(sheets.map { sheetName(it.name) })
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.part("[Content_Types].xml", contentTypes(sheets.size))
            zip.part("_rels/.rels", ROOT_RELS)
            zip.part("xl/workbook.xml", workbook(names))
            zip.part("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            zip.part("xl/styles.xml", STYLES)
            sheets.forEachIndexed { i, sheet -> zip.part("xl/worksheets/sheet${i + 1}.xml", worksheet(sheet)) }
        }
        return out.toByteArray()
    }

    fun excelSerial(date: LocalDate): Long = ChronoUnit.DAYS.between(EXCEL_EPOCH, date)

    /** Column letters: 0 -> A, 25 -> Z, 26 -> AA. */
    fun columnName(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append('A' + rem)
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    /** Excel sheet names: at most 31 characters and none of \ / ? * [ ] : */
    fun sheetName(name: String): String {
        val cleaned = name.filterNot { it in "\\/?*[]:" }.trim().ifEmpty { "Sheet" }
        if (cleaned.length <= 31) return cleaned
        val cut = if (cleaned[30].isHighSurrogate()) 30 else 31
        return cleaned.substring(0, cut)
    }

    private fun uniqueNames(names: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        return names.mapIndexed { i, n ->
            var candidate = n
            if (!seen.add(candidate.lowercase())) {
                candidate = sheetName(n.take(27) + " (${i + 1})")
                seen.add(candidate.lowercase())
            }
            candidate
        }
    }

    private fun ZipOutputStream.part(path: String, xml: String) {
        putNextEntry(ZipEntry(path))
        write(xml.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun worksheet(sheet: Sheet): String = buildString {
        append(XML_HEAD)
        append("""<worksheet xmlns="$MAIN_NS">""")
        if (sheet.freezeFirstRow) {
            append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        }
        if (sheet.columnWidths.isNotEmpty()) {
            append("<cols>")
            sheet.columnWidths.forEachIndexed { i, w ->
                append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""")
            }
            append("</cols>")
        }
        append("<sheetData>")
        sheet.rows.forEachIndexed { r, row ->
            append("""<row r="${r + 1}">""")
            row.forEachIndexed { c, cell -> appendCell("${columnName(c)}${r + 1}", cell) }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun StringBuilder.appendCell(ref: String, cell: Cell) {
        when (cell) {
            is Cell.Text -> append("""<c r="$ref" t="inlineStr" s="${if (cell.bold) STYLE_BOLD else STYLE_TEXT}"><is><t xml:space="preserve">${escape(cell.value)}</t></is></c>""")
            is Cell.Amount -> append("""<c r="$ref" s="${if (cell.bold) STYLE_AMOUNT_BOLD else STYLE_AMOUNT}"><v>${MoneyFormatter.plain(cell.money)}</v></c>""")
            is Cell.Day -> append("""<c r="$ref" s="$STYLE_DATE"><v>${excelSerial(cell.date)}</v></c>""")
            is Cell.Count -> append("""<c r="$ref" s="$STYLE_COUNT"><v>${cell.value}</v></c>""")
            is Cell.Percent -> append("""<c r="$ref" s="$STYLE_PERCENT"><v>${percentText(cell.basisPoints)}</v></c>""")
            Cell.Empty -> Unit
        }
    }

    /** Escapes XML specials and drops characters XML 1.0 cannot carry (e.g. stray control codes). */
    fun escape(value: String): String {
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val cp = value.codePointAt(i)
            when {
                cp == '&'.code -> sb.append("&amp;")
                cp == '<'.code -> sb.append("&lt;")
                cp == '>'.code -> sb.append("&gt;")
                cp == '"'.code -> sb.append("&quot;")
                cp == 0x9 || cp == 0xA || cp == 0xD || cp in 0x20..0xD7FF || cp in 0xE000..0xFFFD || cp in 0x10000..0x10FFFF ->
                    sb.appendCodePoint(cp)
                else -> Unit
            }
            i += Character.charCount(cp)
        }
        return sb.toString()
    }

    private fun contentTypes(sheetCount: Int) = buildString {
        append(XML_HEAD)
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..sheetCount) {
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    private fun workbook(names: List<String>) = buildString {
        append(XML_HEAD)
        append("""<workbook xmlns="$MAIN_NS" xmlns:r="$REL_NS"><sheets>""")
        names.forEachIndexed { i, n -> append("""<sheet name="${escape(n)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""") }
        append("</sheets></workbook>")
    }

    private fun workbookRels(sheetCount: Int) = buildString {
        append(XML_HEAD)
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..sheetCount) {
            append("""<Relationship Id="rId$i" Type="$REL_NS/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        append("""<Relationship Id="rId${sheetCount + 1}" Type="$REL_NS/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private const val XML_HEAD = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""
    private const val MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    private const val ROOT_RELS = XML_HEAD +
        """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
        """<Relationship Id="rId1" Type="$REL_NS/officeDocument" Target="xl/workbook.xml"/>""" +
        "</Relationships>"

    /** numFmt 4 is the built-in "#,##0.00"; 164 = day-month-year; 165 = number with a % sign (value is already in percent). */
    private const val STYLES = XML_HEAD +
        """<styleSheet xmlns="$MAIN_NS">""" +
        """<numFmts count="2"><numFmt numFmtId="164" formatCode="dd\-mm\-yyyy"/><numFmt numFmtId="165" formatCode="0.00&quot;%&quot;"/></numFmts>""" +
        """<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>""" +
        """<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>""" +
        """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
        """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
        """<cellXfs count="7">""" +
        """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
        """<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>""" +
        """<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
        """<xf numFmtId="4" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
        """<xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
        """<xf numFmtId="1" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
        """<xf numFmtId="4" fontId="1" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1"/>""" +
        "</cellXfs>" +
        """<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>""" +
        "</styleSheet>"
}
