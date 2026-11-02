package com.family.finance

import android.net.Uri
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.family.finance.data.AppContainer
import com.family.finance.data.legacy.LegacyDbReader
import com.family.finance.domain.calc.IncomeExpenseTotals
import com.family.finance.domain.legacy.LegacyImport
import com.family.finance.domain.model.Money
import com.family.finance.domain.model.UserId
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.Instant

/**
 * Practice run of the old-app import on the family's real file (PRD 13.2 step 7: staging).
 * Skipped unless run on purpose with the file's path and the expected totals as arguments,
 * so the real file and its figures never live in the repository. Writes only to an
 * in-memory store; prints counts and totals, never individual entries.
 */
class LegacyRealFileDryRunTest {

    @Test fun realFileImportsCompletelyAndTotalsMatch() = runBlocking<Unit> {
        val args = InstrumentationRegistry.getArguments()
        val path = args.getString("legacyDb")
        assumeTrue("Run with -e legacyDb <path on phone>", path != null)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext

        // Apps cannot read the shell's temp folder, so copy the file in through the test shell.
        val copy = File(context.cacheDir, "dry-run.db")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("cat $path")).use { input ->
            copy.outputStream().use { input.copyTo(it) }
        }
        val source = try {
            LegacyDbReader(context).read(Uri.fromFile(copy))
        } finally {
            copy.delete()
        }
        val plan = LegacyImport.plan(source, UserId("dry-run"), Instant.now(), "dry run")
        val r = plan.report
        val c = AppContainer(currentUser = { UserId("dry-run") })
        val restore = c.backupService.plan(plan.records)
        c.backupService.restore(restore)
        val stored = IncomeExpenseTotals.of(c.transactionRepository.observeAll().first())

        val lines = listOf(
            "rows=${r.totalRows} deletedSkipped=${r.deletedSkipped} imported=${r.imported} rejected=${r.rejectedCount} ${r.rejected.mapValues { it.value.size }}",
            "untitled=${r.untitled} bills=${r.withBills} possibleDuplicateGroups=${r.possibleDuplicateGroups} notes=${r.notesImported}",
            "categoriesUsed=${r.categoryMappings.size} unused=${r.unusedCategories} accounts=${r.accounts.size} (entries per account ${r.accounts.map { it.entries }})",
            "dates=${r.firstDate}..${r.lastDate}",
            "report income=${r.incomeTotal.paise} expense=${r.expenseTotal.paise} net=${r.net.paise}",
            "stored  income=${stored.income.paise} expense=${stored.expense.paise} recordsAdded=${restore.addCount}",
        )
        lines.forEach { Log.i("LegacyDryRun", it) }

        assertThat(r.rejectedCount).isEqualTo(0)
        assertThat(r.imported + r.deletedSkipped).isEqualTo(r.totalRows)
        assertThat(stored).isEqualTo(IncomeExpenseTotals(r.incomeTotal, r.expenseTotal))
        args.getString("expectIncomePaise")?.let { assertThat(r.incomeTotal).isEqualTo(Money(it.toLong())) }
        args.getString("expectExpensePaise")?.let { assertThat(r.expenseTotal).isEqualTo(Money(it.toLong())) }
        args.getString("expectImported")?.let { assertThat(r.imported).isEqualTo(it.toInt()) }
    }
}
