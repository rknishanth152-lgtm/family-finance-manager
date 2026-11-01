package com.family.finance.domain.export

/**
 * The statement workbook (PRD 12.3): Summary, Transactions, Category Breakdown and
 * Account Summary sheets, with real dates and amounts and Tamil text as typed.
 */
object XlsxExporter {

    fun export(input: StatementInput): ByteArray = XlsxWriter.write(sheets(input))

    fun sheets(input: StatementInput): List<XlsxWriter.Sheet> {
        val l = input.labels
        val r = input.report
        return listOf(
            XlsxWriter.Sheet(l[ExportText.SHEET_SUMMARY], summaryRows(input), columnWidths = listOf(34, 40)),
            XlsxWriter.Sheet(
                l[ExportText.SHEET_TRANSACTIONS],
                listOf(StatementTables.transactionHeader(l).map { Cell.Text(it, bold = true) }) +
                    r.transactions.map { StatementTables.transactionRow(it, l) },
                columnWidths = listOf(12, 12, 30, 22, 22, 22, 14, 14, 14, 40, 16),
                freezeFirstRow = true,
            ),
            XlsxWriter.Sheet(l[ExportText.SHEET_CATEGORIES], categoryRows(input), columnWidths = listOf(14, 30, 16, 10, 10)),
            XlsxWriter.Sheet(
                l[ExportText.SHEET_ACCOUNTS],
                listOf(StatementTables.accountHeader(l).map { Cell.Text(it, bold = true) }) + StatementTables.accountRows(input),
                columnWidths = listOf(24, 16, 16, 16, 16),
                freezeFirstRow = true,
            ),
        )
    }

    private fun summaryRows(input: StatementInput): List<List<Cell>> {
        val l = input.labels
        return buildList {
            add(listOf(Cell.Text(l[ExportText.STATEMENT_TITLE], bold = true)))
            add(listOf(Cell.Text(l[ExportText.FAMILY]), Cell.Text(input.familyName)))
            add(listOf(Cell.Text(l[ExportText.PERIOD]), Cell.Text(StatementTables.periodText(input))))
            add(listOf(Cell.Text(l[ExportText.GENERATED_ON]), Cell.Text(l.longDate(input.generatedOn))))
            add(listOf(Cell.Text(l[ExportText.FILTERS]), Cell.Text(StatementTables.filterText(input))))
            add(emptyList())
            StatementTables.summaryLines(input).forEach { (label, money) ->
                add(listOf(Cell.Text(label, bold = true), Cell.Amount(money, bold = true)))
            }
            if (input.report.balances == null) add(listOf(Cell.Text(l[ExportText.BALANCES_NOT_SHOWN])))
            if (input.report.incomeBySource.isNotEmpty()) {
                add(emptyList())
                add(listOf(Cell.Text(l[ExportText.SECTION_SOURCES], bold = true)))
                add(listOf(ExportText.COL_SOURCE, ExportText.COL_AMOUNT, ExportText.COL_SHARE, ExportText.COL_COUNT).map { Cell.Text(l[it], bold = true) })
                addAll(StatementTables.sourceRows(input))
            }
        }
    }

    private fun categoryRows(input: StatementInput): List<List<Cell>> {
        val l = input.labels
        val r = input.report
        val header = listOf(ExportText.COL_KIND, ExportText.COL_CATEGORY, ExportText.COL_AMOUNT, ExportText.COL_SHARE, ExportText.COL_COUNT)
            .map { Cell.Text(l[it], bold = true) }
        val expense = StatementTables.categoryRows(r.expenseByCategory, r.totals.expense, l).map { listOf(Cell.Text(l[ExportText.TYPE_EXPENSE])) + it }
        val income = StatementTables.categoryRows(r.incomeByCategory, r.totals.income, l).map { listOf(Cell.Text(l[ExportText.TYPE_INCOME])) + it }
        return listOf(header) + expense + income
    }
}
