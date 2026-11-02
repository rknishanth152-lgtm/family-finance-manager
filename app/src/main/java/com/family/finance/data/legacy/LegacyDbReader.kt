package com.family.finance.data.legacy

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.net.Uri
import com.family.finance.domain.legacy.LegacyCategory
import com.family.finance.domain.legacy.LegacyEntry
import com.family.finance.domain.legacy.LegacyNote
import com.family.finance.domain.legacy.LegacyPaymentMethod
import com.family.finance.domain.legacy.LegacySource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** The chosen file is not a backup of the old app (or is a version this reader does not know). */
class LegacyFormatException(message: String) : Exception(message)

/**
 * Reads the backup file of the family's previous app (a SQLite database). The file is
 * copied into this app's private cache, opened read-only, and the copy deleted afterwards;
 * the person's original file is never changed (PRD 13.3).
 *
 * Amounts are converted to whole paise inside SQLite, together with a check that no
 * amount had more than two decimals, so no floating-point money reaches Kotlin.
 */
class LegacyDbReader(context: Context) {

    private val app = context.applicationContext

    suspend fun read(uri: Uri): LegacySource = withContext(Dispatchers.IO) {
        val dir = File(app.cacheDir, WORK_DIR).apply { deleteRecursively(); mkdirs() }
        val copy = File(dir, "old.db")
        try {
            val input = app.contentResolver.openInputStream(uri) ?: throw LegacyFormatException("cannot open file")
            input.use { src -> copy.outputStream().use { src.copyTo(it) } }
            val db = try {
                SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READONLY)
            } catch (e: RuntimeException) {
                throw LegacyFormatException("not a database")
            }
            // SQLite opens any file lazily; a photo or a damaged file only fails at the first query.
            db.use {
                try {
                    read(it)
                } catch (e: SQLiteException) {
                    throw LegacyFormatException("not a readable database")
                }
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Also used directly by tests with a database they built. */
    fun read(db: SQLiteDatabase): LegacySource {
        val main = columns(db, "incomeexpense")
        requireColumns("incomeexpense", main, "id", "date", "income", "expense", "notes", "category", "paymentMethod", "isDeleted")
        requireColumns("category", columns(db, "category"), "id", "name", "type")
        requireColumns("paymentMethod", columns(db, "paymentMethod"), "_id", "name")

        val billColumns = listOf("bill", "bill2", "bill3").filter { it in main }
        val hasBill = if (billColumns.isEmpty()) "0" else billColumns.joinToString(" OR ") { "coalesce($it,'') <> ''" }
        val entries = db.rawQuery(
            """
            SELECT id, date,
                   CAST(round(income * 100) AS INTEGER), CAST(round(expense * 100) AS INTEGER),
                   abs(income * 100 - round(income * 100)) < 1e-6 AND abs(expense * 100 - round(expense * 100)) < 1e-6,
                   notes, category, paymentMethod, ($hasBill), isDeleted
            FROM incomeexpense ORDER BY id
            """.trimIndent(),
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        LegacyEntry(
                            id = c.getLong(0), epochMillis = c.getLong(1),
                            incomePaise = c.getLong(2), expensePaise = c.getLong(3), exactAmounts = c.getInt(4) == 1,
                            notes = c.getStringOrNull(5), category = c.getStringOrNull(6), paymentMethod = c.getStringOrNull(7),
                            hasBill = c.getInt(8) == 1, isDeleted = c.getInt(9) != 0,
                        ),
                    )
                }
            }
        }
        val categories = db.rawQuery("SELECT id, name, type FROM category ORDER BY id", null).use { c ->
            buildList { while (c.moveToNext()) add(LegacyCategory(c.getLong(0), c.getString(1), c.getStringOrNull(2).equals("Income", ignoreCase = true))) }
        }
        val methods = db.rawQuery("SELECT _id, name FROM paymentMethod ORDER BY _id", null).use { c ->
            buildList { while (c.moveToNext()) add(LegacyPaymentMethod(c.getLong(0), c.getStringOrNull(1).orEmpty())) }
        }
        val notes = if (columns(db, "notebook").containsAll(listOf("id", "date", "note"))) {
            db.rawQuery("SELECT id, date, note FROM notebook ORDER BY id", null).use { c ->
                buildList { while (c.moveToNext()) add(LegacyNote(c.getLong(0), c.getLong(1), c.getStringOrNull(2))) }
            }
        } else {
            emptyList()
        }
        return LegacySource(entries, categories, methods, notes)
    }

    private fun columns(db: SQLiteDatabase, table: String): Set<String> =
        db.rawQuery("PRAGMA table_info(`$table`)", null).use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
        }

    private fun requireColumns(table: String, present: Set<String>, vararg needed: String) {
        val missing = needed.filter { it !in present }
        if (missing.isNotEmpty()) throw LegacyFormatException("$table is missing ${missing.joinToString()}")
    }

    private fun android.database.Cursor.getStringOrNull(i: Int): String? = if (isNull(i)) null else getString(i)

    private companion object {
        const val WORK_DIR = "legacy-import"
    }
}
