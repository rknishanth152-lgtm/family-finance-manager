package com.family.finance.domain.export

import com.family.finance.domain.Fixtures.BANK_ID
import com.family.finance.domain.Fixtures.CASH_ID
import com.family.finance.domain.Fixtures.GROCERIES_ID
import com.family.finance.domain.Fixtures.SALARY_ID
import com.family.finance.domain.Fixtures.account
import com.family.finance.domain.Fixtures.category
import com.family.finance.domain.Fixtures.date
import com.family.finance.domain.Fixtures.expense
import com.family.finance.domain.Fixtures.income
import com.family.finance.domain.Fixtures.rupees
import com.family.finance.domain.Fixtures.transfer
import com.family.finance.domain.calc.DateRange
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.CategoryId
import com.family.finance.domain.model.CategoryKind
import com.family.finance.domain.model.Money
import com.family.finance.domain.model.UserId
import com.family.finance.domain.report.ReportFilter
import com.family.finance.domain.report.Reports
import java.time.LocalDate

/** Labels that make the language visible in assertions: "[ta] COL_DATE". */
class FakeLabels(private val lang: String = "ta") : ExportLabels {
    override fun get(text: ExportText) = "[$lang] $text"
    override fun category(id: CategoryId) = "cat:${id.value}"
    override fun account(id: AccountId) = "acc:${id.value}"
    override fun person(id: UserId) = "அப்பா"
    override fun date(date: LocalDate) = date.toString()
    override fun longDate(date: LocalDate) = "long:$date"
    override fun page(number: Int, of: Int) = "$number/$of"
}

object ExportSamples {
    const val TAMIL_TITLE = "பால் விற்பனை – சுப்பிரமணி"
    const val TAMIL_NOTE = "காலை 10 லிட்டர், மாலை 8 லிட்டர் (\"Rate\" ₹42)"
    const val FORMULA_NOTE = "=HYPERLINK(\"http://evil\",\"click\")"

    val range = DateRange(date("2026-03-01"), date("2026-03-31"))

    fun input(): StatementInput {
        val txns = listOf(
            income(Money(1_500_50), BANK_ID, on = "2026-03-05").copy(title = TAMIL_TITLE, notes = TAMIL_NOTE, source = "கூட்டுறவு சங்கம்"),
            expense(rupees(400), CASH_ID, on = "2026-03-06").copy(notes = FORMULA_NOTE, merchant = "+91 shop"),
            transfer(rupees(1_000), from = BANK_ID, to = CASH_ID, on = "2026-03-07"),
        )
        val accounts = listOf(account(CASH_ID, opening = rupees(500)), account(BANK_ID, opening = rupees(10_000)))
        val categories = listOf(category(SALARY_ID, CategoryKind.INCOME), category(GROCERIES_ID, CategoryKind.EXPENSE))
        val report = Reports.build(ReportFilter(range), txns, accounts, categories)
        return StatementInput(report, familyName = "குடும்பம்", generatedOn = date("2026-11-01"), labels = FakeLabels())
    }
}
