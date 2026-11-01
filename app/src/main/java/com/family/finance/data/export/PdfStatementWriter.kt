package com.family.finance.data.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.withTranslation
import com.family.finance.R
import com.family.finance.domain.calc.IncomeExpenseTotals
import com.family.finance.domain.export.Cell
import com.family.finance.domain.export.ExportText
import com.family.finance.domain.export.StatementInput
import com.family.finance.domain.export.StatementTables
import com.family.finance.domain.export.percentText
import com.family.finance.domain.model.Money
import com.family.finance.domain.model.MoneyFormatter
import com.family.finance.domain.model.Transaction
import com.family.finance.domain.report.CategoryTotal
import com.family.finance.domain.report.Reports
import java.io.OutputStream

/**
 * The PDF statement (PRD 12.1), drawn with Android's own text engine so Tamil letters that
 * combine are shaped correctly, using the bundled Noto Sans Tamil font. Android embeds the
 * glyphs it draws, so the file looks the same on any phone or computer.
 *
 * Layout is done in two passes: every block is measured and placed on pages first, so the
 * footer can say "Page 2 of 5", and only then drawn.
 */
class PdfStatementWriter(context: Context) {

    /** One bar pair of the chart, already labelled in the export's language. */
    data class ChartBar(val label: String, val totals: IncomeExpenseTotals)

    private val regular: Typeface = checkNotNull(ResourcesCompat.getFont(context, R.font.noto_sans_tamil_regular))
    private val bold: Typeface = checkNotNull(ResourcesCompat.getFont(context, R.font.noto_sans_tamil_bold))

    private val body = paint(regular, 8.5f, INK)
    private val bodyBold = paint(bold, 8.5f, INK)
    private val small = paint(regular, 7.5f, MUTED)
    private val heading = paint(bold, 11f, INK)
    private val title = paint(bold, 17f, INK)
    private val incomeBold = paint(bold, 8.5f, INCOME)
    private val expenseBold = paint(bold, 8.5f, EXPENSE)

    fun write(input: StatementInput, chart: List<ChartBar>, out: OutputStream) {
        val items = buildItems(input, chart)
        val pages = paginate(items)
        val doc = PdfDocument()
        try {
            pages.forEachIndexed { index, placed ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, index + 1).create())
                placed.forEach { (block, top) -> block.draw(page.canvas, top) }
                drawFooter(page.canvas, input, index + 1, pages.size)
                doc.finishPage(page)
            }
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    // ---- Content -------------------------------------------------------------------------

    private fun buildItems(input: StatementInput, chart: List<ChartBar>): List<Item> {
        val l = input.labels
        val r = input.report
        val items = mutableListOf<Item>()

        items += Item(TextBlock(l[ExportText.STATEMENT_TITLE], title, bottomGap = 6f))
        items += Item(KeyValueBlock(
            listOf(
                l[ExportText.FAMILY] to input.familyName,
                l[ExportText.PERIOD] to StatementTables.periodText(input),
                l[ExportText.GENERATED_ON] to l.longDate(input.generatedOn),
                l[ExportText.FILTERS] to StatementTables.filterText(input),
            ),
        ))

        items += headingItem(l[ExportText.SECTION_SUMMARY])
        val lines = StatementTables.summaryLines(input)
        val summaryRows = lines.map { (label, money) ->
            val strong = label == l[ExportText.CLOSING] || label == l[ExportText.BALANCE]
            listOf(TextCell(label, if (strong) bodyBold else body), TextCell(MoneyFormatter.format(money), if (strong) bodyBold else body, Layout.Alignment.ALIGN_OPPOSITE))
        }
        val summary = Table(listOf(0.6f, 0.4f), header = null)
        summaryRows.forEach { items += Item(summary.row(it, zebra = false)) }
        if (r.balances == null) items += Item(TextBlock(l[ExportText.BALANCES_NOT_SHOWN], small, topGap = 4f))

        if (chart.size > 1 && (!r.totals.income.isZero || !r.totals.expense.isZero)) {
            items += headingItem(l[ExportText.SECTION_CHART])
            items += Item(ChartBlock(chart, l[ExportText.TYPE_INCOME], l[ExportText.TYPE_EXPENSE]))
        }

        categorySection(items, l[ExportText.SECTION_EXPENSE_CATEGORIES], r.expenseByCategory, r.totals.expense, input)
        categorySection(items, l[ExportText.SECTION_INCOME_CATEGORIES], r.incomeByCategory, r.totals.income, input)

        if (r.incomeBySource.isNotEmpty()) {
            items += headingItem(l[ExportText.SECTION_SOURCES])
            val table = Table(listOf(0.5f, 0.25f, 0.25f), header = listOf(l[ExportText.COL_SOURCE], l[ExportText.COL_AMOUNT], l[ExportText.COL_SHARE]))
            items += Item(table.headerBlock, keepWithNext = true)
            StatementTables.sourceRows(input).forEach { row ->
                items += Item(table.row(listOf(text(row[0]), amount(row[1]), percent(row[2])), zebra = true), table.headerBlock)
            }
        }

        if (r.accounts.isNotEmpty()) {
            items += headingItem(l[ExportText.SECTION_ACCOUNTS])
            val table = Table(listOf(0.28f, 0.18f, 0.18f, 0.18f, 0.18f), header = StatementTables.accountHeader(l))
            items += Item(table.headerBlock, keepWithNext = true)
            StatementTables.accountRows(input).forEach { row ->
                items += Item(table.row(listOf(text(row[0])) + row.drop(1).map { amount(it) }, zebra = true), table.headerBlock)
            }
        }

        items += headingItem(l[ExportText.SECTION_TRANSACTIONS])
        val tx = Table(
            listOf(0.12f, 0.43f, 0.15f, 0.15f, 0.15f),
            header = listOf(l[ExportText.COL_DATE], l[ExportText.COL_TITLE], l[ExportText.COL_INCOME], l[ExportText.COL_EXPENSE], l[ExportText.COL_TRANSFER]),
            textColumns = 2,
        )
        items += Item(tx.headerBlock, keepWithNext = true)
        if (r.transactions.isEmpty()) items += Item(TextBlock(l[ExportText.NO_TRANSACTIONS], small, topGap = 4f))
        r.transactions.forEach { t -> items += Item(tx.row(transactionCells(t, input), zebra = true), tx.headerBlock) }
        return items
    }

    private fun categorySection(items: MutableList<Item>, name: String, totals: List<CategoryTotal>, whole: Money, input: StatementInput) {
        if (totals.isEmpty()) return
        val l = input.labels
        items += headingItem(name)
        val table = Table(listOf(0.42f, 0.22f, 0.36f), header = listOf(l[ExportText.COL_CATEGORY], l[ExportText.COL_AMOUNT], l[ExportText.COL_SHARE]))
        items += Item(table.headerBlock, keepWithNext = true)
        totals.forEach {
            val share = Reports.shareBasisPoints(it.total, whole)
            items += Item(
                table.row(
                    listOf(
                        TextCell(l.category(it.categoryId), body),
                        TextCell(MoneyFormatter.format(it.total), body, Layout.Alignment.ALIGN_OPPOSITE),
                        BarCell(share, "${percentText(share)}%"),
                    ),
                    zebra = true,
                ),
                table.headerBlock,
            )
        }
    }

    /** Typed text is printed in full (wrapped, never cut): title, then category · party · account, then notes. */
    private fun transactionCells(t: Transaction, input: StatementInput): List<PdfCell> {
        val l = input.labels
        val row = StatementTables.transactionRow(t, l)
        fun s(i: Int) = (row[i] as? Cell.Text)?.value
        val details = listOfNotNull(s(3), s(4), s(5)).joinToString(" · ")
        val stack = StackCell(
            listOfNotNull(
                (t.title.ifBlank { l[ExportText.TYPE_TRANSFER] }) to bodyBold,
                details.ifEmpty { null }?.let { it to small },
                t.notes?.takeIf { it.isNotBlank() }?.let { it to small },
            ),
        )
        return listOf(
            TextCell(l.date(t.date), body),
            stack,
            if (t is Transaction.Income) TextCell(MoneyFormatter.format(t.amount), incomeBold, Layout.Alignment.ALIGN_OPPOSITE) else EmptyCell,
            if (t is Transaction.Expense) TextCell(MoneyFormatter.format(t.amount), expenseBold, Layout.Alignment.ALIGN_OPPOSITE) else EmptyCell,
            if (t is Transaction.Transfer) TextCell(MoneyFormatter.format(t.amount), body, Layout.Alignment.ALIGN_OPPOSITE) else EmptyCell,
        )
    }

    private fun text(cell: Cell): PdfCell = when (cell) {
        is Cell.Text -> TextCell(cell.value, if (cell.bold) bodyBold else body)
        else -> EmptyCell
    }

    private fun amount(cell: Cell): PdfCell = when (cell) {
        is Cell.Amount -> TextCell(MoneyFormatter.format(cell.money), body, Layout.Alignment.ALIGN_OPPOSITE)
        else -> EmptyCell
    }

    private fun percent(cell: Cell): PdfCell = when (cell) {
        is Cell.Percent -> TextCell("${percentText(cell.basisPoints)}%", body, Layout.Alignment.ALIGN_OPPOSITE)
        else -> EmptyCell
    }

    private fun headingItem(text: String) = Item(TextBlock(text, heading, topGap = 14f, bottomGap = 4f), keepWithNext = true)

    private fun drawFooter(canvas: Canvas, input: StatementInput, page: Int, of: Int) {
        val y = (PAGE_H - 22).toFloat()
        canvas.drawLine(MARGIN, y - 10f, PAGE_W - MARGIN, y - 10f, RULE)
        val left = layout(input.familyName, small, CONTENT_W * 0.6f, Layout.Alignment.ALIGN_NORMAL, maxLines = 1)
        val right = layout(input.labels.page(page, of), small, CONTENT_W * 0.4f, Layout.Alignment.ALIGN_OPPOSITE, maxLines = 1)
        draw(canvas, left, MARGIN, y - 6f)
        draw(canvas, right, MARGIN + CONTENT_W * 0.6f, y - 6f)
    }

    // ---- Pagination ----------------------------------------------------------------------

    private class Item(val block: Block, val repeatHeader: Block? = null, val keepWithNext: Boolean = false)

    private fun paginate(items: List<Item>): List<List<Pair<Block, Float>>> {
        val pages = mutableListOf<MutableList<Pair<Block, Float>>>(mutableListOf())
        var y = MARGIN
        val bottom = PAGE_H - FOOTER_SPACE
        fun newPage(repeat: Block?) {
            pages += mutableListOf<Pair<Block, Float>>()
            y = MARGIN
            if (repeat != null) {
                pages.last() += repeat to y
                y += repeat.height
            }
        }
        items.forEachIndexed { i, item ->
            val needed = item.block.height + if (item.keepWithNext) items.getOrNull(i + 1)?.block?.height ?: 0f else 0f
            if (y + needed > bottom && y > MARGIN) newPage(item.repeatHeader.takeIf { it !== item.block })
            pages.last() += item.block to y
            y += item.block.height
        }
        return pages
    }

    // ---- Blocks --------------------------------------------------------------------------

    private interface Block {
        val height: Float
        fun draw(canvas: Canvas, top: Float)
    }

    private inner class TextBlock(text: String, paint: TextPaint, private val topGap: Float = 0f, bottomGap: Float = 0f) : Block {
        private val layout = layout(text, paint, CONTENT_W, Layout.Alignment.ALIGN_NORMAL)
        override val height = topGap + layout.height + bottomGap
        override fun draw(canvas: Canvas, top: Float) = draw(canvas, layout, MARGIN, top + topGap)
    }

    private inner class KeyValueBlock(pairs: List<Pair<String, String>>) : Block {
        private val keyWidth = CONTENT_W * 0.22f
        private val rows = pairs.map { (k, v) ->
            layout(k, small, keyWidth - 6f, Layout.Alignment.ALIGN_NORMAL) to layout(v, body, CONTENT_W - keyWidth, Layout.Alignment.ALIGN_NORMAL)
        }
        override val height = rows.sumOf { (k, v) -> maxOf(k.height, v.height).toDouble() + 3 }.toFloat()
        override fun draw(canvas: Canvas, top: Float) {
            var y = top
            rows.forEach { (k, v) ->
                draw(canvas, k, MARGIN, y)
                draw(canvas, v, MARGIN + keyWidth, y)
                y += maxOf(k.height, v.height) + 3
            }
        }
    }

    private sealed interface PdfCell
    private data object EmptyCell : PdfCell
    private class TextCell(val text: String, val paint: TextPaint, val align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL) : PdfCell
    private class StackCell(val lines: List<Pair<String, TextPaint>>) : PdfCell
    /** A share bar with its percentage; [basisPoints] 10 000 = full width. */
    private class BarCell(val basisPoints: Long, val label: String) : PdfCell

    /** Columns after the first [textColumns] hold amounts, so their headings align right. */
    private inner class Table(fractions: List<Float>, header: List<String>?, textColumns: Int = 1) {
        val widths = fractions.map { it * CONTENT_W }
        private var rows = 0
        private val headerRow: Block? = header?.let { names ->
            RowBlock(
                names.mapIndexed { i, n -> TextCell(n, bodyBold, if (i < textColumns) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_OPPOSITE) },
                HEADER_FILL,
            )
        }
        val headerBlock: Block get() = checkNotNull(headerRow)

        /** Every other striped row is shaded, for reading across wide rows. */
        fun row(cells: List<PdfCell>, zebra: Boolean): Block =
            RowBlock(cells, if (zebra && rows++ % 2 == 1) ZEBRA_FILL else null)

        private inner class RowBlock(cells: List<PdfCell>, private val fill: Int?) : Block {
            private val laid: List<List<StaticLayout>> = cells.mapIndexed { i, cell ->
                val w = widths[i] - 2 * CELL_PAD
                when (cell) {
                    EmptyCell -> emptyList()
                    is TextCell -> listOf(layout(cell.text, cell.paint, w, cell.align))
                    is StackCell -> cell.lines.map { (t, p) -> layout(t, p, w, Layout.Alignment.ALIGN_NORMAL) }
                    is BarCell -> listOf(layout(cell.label, small, w, Layout.Alignment.ALIGN_OPPOSITE))
                }
            }
            private val bars: Map<Int, Long> = cells.withIndex().filter { it.value is BarCell }.associate { it.index to (it.value as BarCell).basisPoints }
            override val height = (laid.maxOf { col -> col.sumOf { it.height } } + 2 * CELL_PAD).toFloat()

            override fun draw(canvas: Canvas, top: Float) {
                if (fill != null) canvas.drawRect(MARGIN, top, MARGIN + CONTENT_W, top + height, Paint().apply { color = fill })
                var x = MARGIN
                laid.forEachIndexed { i, col ->
                    bars[i]?.let { bp ->
                        val barMax = widths[i] * 0.55f
                        val y = top + height / 2
                        canvas.drawRect(x + CELL_PAD, y - 3f, x + CELL_PAD + barMax, y + 3f, Paint().apply { color = BAR_TRACK })
                        canvas.drawRect(x + CELL_PAD, y - 3f, x + CELL_PAD + barMax * bp / 10_000f, y + 3f, Paint().apply { color = BAR })
                    }
                    var y = top + CELL_PAD
                    col.forEach { lay ->
                        draw(canvas, lay, x + CELL_PAD, y)
                        y += lay.height
                    }
                    x += widths[i]
                }
            }
        }
    }

    /** Income and expense bars per day, month or year, scaled to the largest value. */
    private inner class ChartBlock(private val bars: List<ChartBar>, incomeLabel: String, expenseLabel: String) : Block {
        private val plotH = 110f
        private val legendIncome = layout(incomeLabel, small, 120f, Layout.Alignment.ALIGN_NORMAL, maxLines = 1)
        private val legendExpense = layout(expenseLabel, small, 120f, Layout.Alignment.ALIGN_NORMAL, maxLines = 1)
        private val max: Money = bars.maxOf { maxOf(it.totals.income, it.totals.expense) }
        private val maxLabel = layout(MoneyFormatter.format(max), small, 200f, Layout.Alignment.ALIGN_NORMAL, maxLines = 1)
        private val step = CONTENT_W / bars.size
        /** Only every n-th label fits under narrow bars. */
        private val labelEvery = maxOf(1, (LABEL_W / step).toInt() + 1)
        private val labels = bars.map { layout(it.label, small, maxOf(step * labelEvery, LABEL_W), Layout.Alignment.ALIGN_CENTER, maxLines = 1) }
        override val height = 14f + plotH + 14f

        override fun draw(canvas: Canvas, top: Float) {
            draw(canvas, maxLabel, MARGIN, top)
            canvas.drawRect(MARGIN + 210f, top + 3f, MARGIN + 217f, top + 10f, Paint().apply { color = INCOME })
            draw(canvas, legendIncome, MARGIN + 220f, top)
            canvas.drawRect(MARGIN + 300f, top + 3f, MARGIN + 307f, top + 10f, Paint().apply { color = EXPENSE })
            draw(canvas, legendExpense, MARGIN + 310f, top)

            val base = top + 14f + plotH
            canvas.drawLine(MARGIN, base, MARGIN + CONTENT_W, base, RULE)
            val barW = minOf(step * 0.36f, 14f)
            bars.forEachIndexed { i, bar ->
                val cx = MARGIN + step * i + step / 2
                fun h(m: Money) = if (max.isPositive) plotH * Reports.shareBasisPoints(m, max) / 10_000f else 0f
                canvas.drawRect(cx - barW, base - h(bar.totals.income), cx, base, Paint().apply { color = INCOME })
                canvas.drawRect(cx, base - h(bar.totals.expense), cx + barW, base, Paint().apply { color = EXPENSE })
                if (i % labelEvery == 0) {
                    val lay = labels[i]
                    draw(canvas, lay, cx - lay.width / 2f, base + 2f)
                }
            }
        }
    }

    // ---- Helpers -------------------------------------------------------------------------

    private fun layout(text: String, paint: TextPaint, width: Float, align: Layout.Alignment, maxLines: Int = Int.MAX_VALUE): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
            .setAlignment(align)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .build()

    private fun draw(canvas: Canvas, layout: StaticLayout, x: Float, y: Float) {
        canvas.withTranslation(x, y) { layout.draw(this) }
    }

    private fun paint(face: Typeface, size: Float, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face
        textSize = size
        this.color = color
    }

    private companion object {
        // A4 in PostScript points.
        const val PAGE_W = 595
        const val PAGE_H = 842
        const val MARGIN = 40f
        const val CONTENT_W = PAGE_W - 2 * MARGIN
        const val FOOTER_SPACE = 48f
        const val CELL_PAD = 3f
        const val LABEL_W = 34f

        val INK = Color.rgb(0x1C, 0x1B, 0x1F)
        val MUTED = Color.rgb(0x5F, 0x5F, 0x66)
        val INCOME = Color.rgb(0x1B, 0x6E, 0x3A)
        val EXPENSE = Color.rgb(0xB3, 0x26, 0x1E)
        val BAR = Color.rgb(0x41, 0x5F, 0x91)
        val BAR_TRACK = Color.rgb(0xE3, 0xE6, 0xEE)
        val HEADER_FILL = Color.rgb(0xE8, 0xEA, 0xF0)
        val ZEBRA_FILL = Color.rgb(0xF6, 0xF7, 0xF9)
        val RULE = Paint().apply { color = Color.rgb(0xBB, 0xBB, 0xC2); strokeWidth = 0.6f }
    }
}
