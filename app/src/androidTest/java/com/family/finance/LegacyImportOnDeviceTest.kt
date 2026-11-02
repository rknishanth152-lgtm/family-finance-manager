package com.family.finance

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.family.finance.di.AppContainer
import com.family.finance.data.backup.BackupFiles
import com.family.finance.data.legacy.LegacyDbReader
import com.family.finance.data.legacy.LegacyFormatException
import com.family.finance.domain.backup.BackupCodec
import com.family.finance.domain.backup.BackupRead
import com.family.finance.domain.calc.IncomeExpenseTotals
import com.family.finance.domain.legacy.LegacyImport
import com.family.finance.domain.legacy.RejectReason
import com.family.finance.domain.model.Money
import com.family.finance.domain.model.UserId
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.time.Instant

/**
 * The old app's file format read with the real SQLite on a phone. The database is made up
 * here with the old app's table layout; the family's real file is never used in tests.
 */
class LegacyImportOnDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.cacheDir, "legacy-test").apply { deleteRecursively(); mkdirs() }

    /** 2026-03-14 23:30 India time. */
    private val evening = Instant.parse("2026-03-14T18:00:00Z").toEpochMilli()

    private fun oldAppFile(): File {
        val file = File(dir, "incomeexpense.db")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE `accounts` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
            db.execSQL("CREATE TABLE `category` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `balance` REAL NOT NULL, `image` TEXT, `type` TEXT, `budget` REAL NOT NULL)")
            db.execSQL("CREATE TABLE `paymentMethod` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT)")
            db.execSQL("CREATE TABLE `notebook` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` INTEGER NOT NULL, `note` TEXT, `completed` TEXT)")
            db.execSQL(
                "CREATE TABLE `incomeexpense` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` INTEGER NOT NULL, `expense` REAL NOT NULL, `income` REAL NOT NULL, " +
                    "`notes` TEXT, `bill` TEXT, `category` TEXT, `icon` TEXT, `subcategory` TEXT, `paymentMethod` TEXT, `reminder` INTEGER NOT NULL, `reminderInterval` TEXT, " +
                    "`reminderEntryDate` INTEGER NOT NULL, `accounts` TEXT, `isDeleted` INTEGER NOT NULL, `deletedDate` INTEGER NOT NULL, recurringId INTEGER, bill2  TEXT, bill3 TEXT)",
            )
            db.execSQL("INSERT INTO category (id, name, balance, type, budget) VALUES (1, 'உரம்', 0, 'Expense', 0), (2, 'விவசாய வருமானம்', 0, 'Income', 0)")
            db.execSQL("INSERT INTO paymentMethod (_id, name) VALUES (1, 'Cash'), (2, 'Bank')")
            db.execSQL("INSERT INTO notebook (id, date, note) VALUES (1, $evening, 'பழைய குறிப்பு')")
            fun row(id: Int, expense: String, income: String, notes: String?, category: String, method: String, deleted: Int = 0, bill: String? = null) {
                db.execSQL(
                    "INSERT INTO incomeexpense (id, date, expense, income, notes, bill, category, paymentMethod, reminder, reminderEntryDate, isDeleted, deletedDate) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?, 0)",
                    arrayOf<Any?>(id, evening, expense.toDouble(), income.toDouble(), notes, bill, category, method, deleted),
                )
            }
            row(1, "2650.05", "0", "உரம் – யூரியா 10 மூட்டை", "உரம்", "Cash", bill = "bill_1.jpg")
            row(2, "0", "165474.00", "நெல் விற்பனை", "விவசாய வருமானம்", "Bank")
            row(3, "0.10", "0", null, "உரம்", "Cash")
            row(4, "999", "0", "நீக்கப்பட்டது", "உரம்", "Cash", deleted = 1)
            row(5, "1234.567", "0", "மூன்று தசமம்", "உரம்", "Cash")
        }
        return file
    }

    @Test fun readsTheOldFormatWithExactPaise() = runBlocking<Unit> {
        val source = LegacyDbReader(context).read(Uri.fromFile(oldAppFile()))
        val byId = source.entries.associateBy { it.id }
        assertThat(byId.getValue(1).expensePaise).isEqualTo(265_005)
        assertThat(byId.getValue(1).hasBill).isTrue()
        assertThat(byId.getValue(2).incomePaise).isEqualTo(16_547_400)
        assertThat(byId.getValue(3).expensePaise).isEqualTo(10)
        assertThat(byId.getValue(4).isDeleted).isTrue()
        assertThat(byId.getValue(5).exactAmounts).isFalse()
        assertThat(byId.getValue(1).notes).isEqualTo("உரம் – யூரியா 10 மூட்டை")
        assertThat(source.categories.map { it.name to it.isIncome }).containsExactly("உரம்" to false, "விவசாய வருமானம்" to true)
        assertThat(source.notes.single().text).isEqualTo("பழைய குறிப்பு")
        // The working copy is removed after reading.
        assertThat(File(context.cacheDir, "legacy-import").exists()).isFalse()
    }

    @Test fun importsIntoTheAppAndReportsEverything() = runBlocking<Unit> {
        val c = AppContainer(currentUser = { UserId("father") })
        val plan = LegacyImport.plan(LegacyDbReader(context).read(Uri.fromFile(oldAppFile())), UserId("father"), Instant.now(), "குடும்பம்")
        val r = plan.report
        assertThat(r.totalRows).isEqualTo(5)
        assertThat(r.deletedSkipped).isEqualTo(1)
        assertThat(r.rejected).containsExactly(RejectReason.NOT_EXACT_PAISE, listOf(5L))
        assertThat(r.imported).isEqualTo(3)
        assertThat(r.untitled).isEqualTo(1)
        assertThat(r.withBills).isEqualTo(1)
        assertThat(r.incomeTotal).isEqualTo(Money(16_547_400))
        assertThat(r.expenseTotal).isEqualTo(Money(265_015))

        c.backupService.restore(c.backupService.plan(plan.records))
        val stored = c.transactionRepository.observeAll().first()
        assertThat(IncomeExpenseTotals.of(stored)).isEqualTo(IncomeExpenseTotals(r.incomeTotal, r.expenseTotal))
        assertThat(stored.map { it.date.toString() }.toSet()).containsExactly("2026-03-14")
    }

    @Test fun aFileThatIsNotTheOldAppIsRefused() = runBlocking<Unit> {
        val notDb = File(dir, "photo.jpg").apply { writeBytes(ByteArray(2048) { it.toByte() }) }
        assertThrows(LegacyFormatException::class.java) { runBlocking { LegacyDbReader(context).read(Uri.fromFile(notDb)) } }

        val otherDb = File(dir, "other.db")
        SQLiteDatabase.openOrCreateDatabase(otherDb, null).use { it.execSQL("CREATE TABLE something (id INTEGER)") }
        assertThrows(LegacyFormatException::class.java) { runBlocking { LegacyDbReader(context).read(Uri.fromFile(otherDb)) } }
    }

    @Test fun backupFileIsWrittenAndReadBackExactly() = runBlocking<Unit> {
        val c = AppContainer(currentUser = { UserId("father") })
        val plan = LegacyImport.plan(LegacyDbReader(context).read(Uri.fromFile(oldAppFile())), UserId("father"), Instant.now(), "குடும்பம்")
        c.backupService.restore(c.backupService.plan(plan.records))
        val backup = c.backupService.create("எங்கள் குடும்பம்")

        val files = BackupFiles(context)
        val target = Uri.fromFile(File(dir, "family-finance-backup.json"))
        files.write(target, BackupCodec.encode(backup))
        assertThat(BackupCodec.decode(files.read(target))).isEqualTo(BackupRead.Ok(backup))
    }
}
