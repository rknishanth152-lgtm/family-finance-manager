package com.family.finance.domain.legacy

import com.family.finance.data.AppContainer
import com.family.finance.domain.calc.IncomeExpenseTotals
import com.family.finance.domain.model.AccountType
import com.family.finance.domain.model.CategoryKind
import com.family.finance.domain.model.Money
import com.family.finance.domain.model.Transaction
import com.family.finance.domain.model.UserId
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Made-up rows in the old app's layout; the family's real file is never used in tests. */
class LegacyImportTest {

    private val now = Instant.parse("2026-11-01T06:30:00Z")
    private val me = UserId("father")

    /** 2026-03-14 23:30 India time = 2026-03-14 18:00 UTC: must stay the 14th. */
    private val lateEvening = Instant.parse("2026-03-14T18:00:00Z").toEpochMilli()
    /** 2026-03-15 00:30 India time = 2026-03-14 19:00 UTC: must be the 15th. */
    private val justAfterMidnight = Instant.parse("2026-03-14T19:00:00Z").toEpochMilli()

    private val categories = listOf(
        LegacyCategory(1, "Fertilizer", isIncome = false),
        LegacyCategory(2, "விவசாய வருமானம்", isIncome = true),
        LegacyCategory(3, "Unused", isIncome = false),
    )
    private val methods = listOf(LegacyPaymentMethod(10, "Cash"), LegacyPaymentMethod(11, "Bank"), LegacyPaymentMethod(12, "Card"))

    private fun entry(id: Long, income: Long = 0, expense: Long = 0, notes: String? = "குறிப்பு $id", category: String = "Fertilizer",
                      method: String = "Cash", at: Long = lateEvening, deleted: Boolean = false, exact: Boolean = true, bill: Boolean = false) =
        LegacyEntry(id, at, income, expense, exact, notes, category, method, bill, deleted)

    private fun source(vararg entries: LegacyEntry) = LegacySource(entries.toList(), categories, methods, listOf(LegacyNote(1, lateEvening, "பழைய குறிப்பு"), LegacyNote(2, lateEvening, "  ")))

    private fun plan(vararg entries: LegacyEntry) = LegacyImport.plan(source(*entries), me, now, "குடும்பம்")

    @Test fun `income and expense rows become entries with exact paise and Tamil text`() {
        val p = plan(
            entry(1, income = 2_190_785_50, category = "விவசாய வருமானம்", method = "Bank", notes = "பால் விற்பனை – சுப்பிரமணி"),
            entry(2, expense = 2_650_05),
        )
        val income = p.records.transactions.single { it is Transaction.Income } as Transaction.Income
        assertThat(income.amount).isEqualTo(Money(2_190_785_50))
        assertThat(income.title).isEqualTo("பால் விற்பனை – சுப்பிரமணி")
        assertThat(income.source).isEmpty()
        assertThat(income.notes).isNull()
        assertThat(p.report.incomeTotal).isEqualTo(Money(2_190_785_50))
        assertThat(p.report.expenseTotal).isEqualTo(Money(2_650_05))
        assertThat(p.report.net).isEqualTo(Money(2_190_785_50 - 2_650_05))
    }

    @Test fun `dates are the day the person picked, in India time`() {
        val p = plan(entry(1, expense = 100, at = lateEvening), entry(2, expense = 100, at = justAfterMidnight))
        assertThat(p.records.transactions.map { it.date }).containsExactly(LocalDate.parse("2026-03-14"), LocalDate.parse("2026-03-15")).inOrder()
        assertThat(p.records.transactions.first().createdAt).isEqualTo(Instant.ofEpochMilli(lateEvening))
    }

    @Test fun `deleted rows are skipped and counted`() {
        val p = plan(entry(1, expense = 100), entry(2, expense = 500, deleted = true))
        assertThat(p.report.totalRows).isEqualTo(2)
        assertThat(p.report.deletedSkipped).isEqualTo(1)
        assertThat(p.report.imported).isEqualTo(1)
        assertThat(p.report.expenseTotal).isEqualTo(Money(100))
    }

    @Test fun `bad rows are rejected with a reason, never half-imported`() {
        val p = plan(
            entry(1, income = 100, expense = 100),
            entry(2),
            entry(3, expense = 100, exact = false),
            entry(4, expense = 100, category = "Missing"),
            entry(5, expense = 100, method = "Missing"),
            entry(6, income = 100, category = "Fertilizer"),
            entry(7, expense = 100),
        )
        assertThat(p.report.rejected).containsExactly(
            RejectReason.BOTH_AMOUNTS, listOf(1L),
            RejectReason.NO_AMOUNT, listOf(2L),
            RejectReason.NOT_EXACT_PAISE, listOf(3L),
            RejectReason.UNKNOWN_CATEGORY, listOf(4L),
            RejectReason.UNKNOWN_PAYMENT_METHOD, listOf(5L),
            RejectReason.CATEGORY_KIND_MISMATCH, listOf(6L),
        )
        assertThat(p.report.rejectedCount).isEqualTo(6)
        assertThat(p.report.imported).isEqualTo(1)
        assertThat(p.report.imported + p.report.rejectedCount + p.report.deletedSkipped).isEqualTo(p.report.totalRows)
    }

    @Test fun `an entry without a description is titled with its category`() {
        val p = plan(entry(1, expense = 100, notes = null), entry(2, expense = 100, notes = "  "))
        assertThat(p.records.transactions.map { it.title }).containsExactly("Fertilizer", "Fertilizer")
        assertThat(p.report.untitled).isEqualTo(2)
    }

    @Test fun `his own category names are kept and only used ones are created`() {
        val p = plan(entry(1, expense = 100), entry(2, income = 100, category = "விவசாய வருமானம்"))
        assertThat(p.records.categories.map { it.name to it.kind }).containsExactly(
            "Fertilizer" to CategoryKind.EXPENSE, "விவசாய வருமானம்" to CategoryKind.INCOME,
        )
        assertThat(p.report.unusedCategories).isEqualTo(1)
    }

    @Test fun `each used payment method becomes an account starting at zero before the first entry`() {
        val p = plan(entry(1, expense = 100, method = "Cash", at = justAfterMidnight), entry(2, income = 300, category = "விவசாய வருமானம்", method = "Bank"))
        assertThat(p.records.accounts.map { it.name }).containsExactly("Cash", "Bank")
        assertThat(p.records.accounts.single { it.name == "Cash" }.type).isEqualTo(AccountType.CASH)
        assertThat(p.records.accounts.all { it.openingBalance.isZero }).isTrue()
        assertThat(p.records.accounts.map { it.openingBalanceDate }.toSet()).containsExactly(LocalDate.parse("2026-03-14"))
        assertThat(p.report.accounts.single { it.oldName == "Bank" }.income).isEqualTo(Money(300))
    }

    @Test fun `bills and possible duplicates are reported`() {
        val p = plan(entry(1, expense = 100, notes = "same", bill = true), entry(2, expense = 100, notes = "same"))
        assertThat(p.report.withBills).isEqualTo(1)
        assertThat(p.report.possibleDuplicateGroups).isEqualTo(1)
        assertThat(p.records.transactions).hasSize(2)
    }

    @Test fun `notebook entries come across, blank ones are dropped`() {
        val p = plan(entry(1, expense = 100))
        assertThat(p.records.notes.map { it.title }).containsExactly("பழைய குறிப்பு")
        assertThat(p.report.notesImported).isEqualTo(1)
    }

    @Test fun `importing twice adds nothing the second time, and totals match the report`() = runTest {
        val c = AppContainer(clock = Clock.fixed(now, ZoneOffset.UTC), currentUser = { me })
        val p = plan(entry(1, expense = 1_00), entry(2, income = 50_000_00, category = "விவசாய வருமானம்", method = "Bank"))
        c.backupService.restore(c.backupService.plan(p.records))
        val again = c.backupService.plan(p.records)
        assertThat(again.addCount).isEqualTo(0)

        val stored = c.transactionRepository.observeAll().first()
        assertThat(IncomeExpenseTotals.of(stored)).isEqualTo(IncomeExpenseTotals(p.report.incomeTotal, p.report.expenseTotal))
        assertThat(c.categoryRepository.observeAll().first().map { it.name }).containsAtLeast("Fertilizer", "விவசாய வருமானம்")
    }
}
