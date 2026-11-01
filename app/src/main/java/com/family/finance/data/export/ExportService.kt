package com.family.finance.data.export

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.LocaleList
import androidx.annotation.StringRes
import com.family.finance.AppLanguage
import com.family.finance.R
import com.family.finance.domain.export.CsvExporter
import com.family.finance.domain.export.ExportLabels
import com.family.finance.domain.export.ExportText
import com.family.finance.domain.export.StatementInput
import com.family.finance.domain.export.XlsxExporter
import com.family.finance.domain.model.Account
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.Category
import com.family.finance.domain.model.CategoryId
import com.family.finance.domain.model.UserId
import com.family.finance.domain.report.Bucket
import com.family.finance.domain.report.Report
import com.family.finance.domain.report.Reports
import com.family.finance.ui.common.categorySystemLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

enum class ExportFormat(val extension: String, val mimeType: String) {
    PDF("pdf", "application/pdf"),
    CSV("csv", "text/csv"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
}

/** A finished export in the app's private cache, ready to save or share. */
data class ExportedFile(val file: File, val displayName: String, val mimeType: String)

/** What the Reports screen hands over to be exported. */
data class ExportRequest(
    val report: Report,
    val format: ExportFormat,
    val language: AppLanguage,
    val familyName: String,
    val today: LocalDate,
    val accounts: Map<AccountId, Account>,
    val categories: Map<CategoryId, Category>,
    val people: Map<UserId, String>,
)

/**
 * Writes the report as PDF, CSV or Excel in the chosen language, independent of the app's
 * own language (PRD 7.4). Files go to a private cache folder that only this app can read;
 * the previous export is deleted first, so finance files do not pile up on the phone.
 */
class ExportService(context: Context) {

    private val app = context.applicationContext
    private val folder get() = File(app.cacheDir, EXPORT_DIR)

    suspend fun export(request: ExportRequest): ExportedFile = withContext(Dispatchers.Default) {
        val localized = app.inLanguage(request.language.tag)
        val labels = AndroidExportLabels(localized, request)
        val input = StatementInput(request.report, request.familyName, request.today, labels)
        val dir = folder.apply { deleteRecursively(); mkdirs() }
        val range = request.report.filter.range
        val name = "family-finance_${range.start}_${range.end}.${request.format.extension}"
        val file = File(dir, name)
        when (request.format) {
            ExportFormat.CSV -> file.writeBytes(CsvExporter.export(input))
            ExportFormat.XLSX -> file.writeBytes(XlsxExporter.export(input))
            ExportFormat.PDF -> file.outputStream().use { out ->
                PdfStatementWriter(localized).write(input, chartBars(request.report, labels), out)
            }
        }
        ExportedFile(file, name, request.format.mimeType)
    }

    /** Copies a finished export to the place the person picked in the system save dialog. */
    suspend fun saveTo(file: File, uri: Uri) = withContext(Dispatchers.IO) {
        val out = checkNotNull(app.contentResolver.openOutputStream(uri, "wt")) { "Cannot write to $uri" }
        out.use { stream -> file.inputStream().use { it.copyTo(stream) } }
    }

    private fun chartBars(report: Report, labels: AndroidExportLabels): List<PdfStatementWriter.ChartBar> {
        val bucket = Reports.defaultBucket(report.filter.range)
        return report.buckets(bucket).map { PdfStatementWriter.ChartBar(labels.bucket(it.range.start, bucket), it.totals) }
    }

    companion object {
        const val EXPORT_DIR = "exports"
    }
}

/** Resources in a given language, without changing the app's own language. */
fun Context.inLanguage(tag: String): Context {
    val config = Configuration(resources.configuration)
    config.setLocales(LocaleList.forLanguageTags(tag))
    return createConfigurationContext(config)
}

private class AndroidExportLabels(private val context: Context, private val request: ExportRequest) : ExportLabels {

    private val locale = Locale.forLanguageTag(request.language.tag)
    private val longFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
    private val monthFormat = DateTimeFormatter.ofPattern("LLL yy", locale)
    private val unknown = context.getString(R.string.label_unknown)

    override fun get(text: ExportText): String = context.getString(text.res())

    /** A renamed category shows its name; a built-in one shows in the export's language. */
    override fun category(id: CategoryId): String {
        val c = request.categories[id] ?: return unknown
        if (c.name.isNotBlank()) return c.name
        return c.systemKey?.let(::categorySystemLabel)?.let(context::getString) ?: unknown
    }

    override fun account(id: AccountId): String = request.accounts[id]?.name ?: unknown
    override fun person(id: UserId): String = request.people[id] ?: unknown

    /** Day-month-year in digits: compact, and the same in Tamil and English. */
    override fun date(date: LocalDate): String = SHORT.format(date)
    override fun longDate(date: LocalDate): String = longFormat.format(date)
    override fun page(number: Int, of: Int): String = context.getString(R.string.export_page, number, of)

    fun bucket(start: LocalDate, bucket: Bucket): String = when (bucket) {
        Bucket.DAY -> start.dayOfMonth.toString()
        Bucket.MONTH -> monthFormat.format(start)
        Bucket.YEAR -> start.year.toString()
    }

    private companion object {
        val SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    }
}

@StringRes
private fun ExportText.res(): Int = when (this) {
    ExportText.STATEMENT_TITLE -> R.string.export_statement_title
    ExportText.FAMILY -> R.string.family_name_default
    ExportText.PERIOD -> R.string.export_period
    ExportText.GENERATED_ON -> R.string.export_generated_on
    ExportText.FILTERS -> R.string.export_includes
    ExportText.ALL_RECORDS -> R.string.export_all_entries
    ExportText.OPENING -> R.string.summary_opening
    ExportText.OPENING_ADDED -> R.string.reports_opening_added
    ExportText.INCOME -> R.string.export_total_income
    ExportText.EXPENSE -> R.string.export_total_expense
    ExportText.BALANCE -> R.string.summary_balance
    ExportText.TRANSFERS_IN -> R.string.reports_transfers_in
    ExportText.TRANSFERS_OUT -> R.string.reports_transfers_out
    ExportText.CLOSING -> R.string.summary_closing
    ExportText.LOAN_RECEIVED_INCLUDED -> R.string.reports_loans_received
    ExportText.LOAN_REPAID_INCLUDED -> R.string.reports_loans_repaid
    ExportText.BALANCES_NOT_SHOWN -> R.string.reports_balances_hidden
    ExportText.COL_DATE -> R.string.field_date
    ExportText.COL_TYPE, ExportText.COL_KIND -> R.string.export_col_type
    ExportText.COL_TITLE -> R.string.field_title
    ExportText.COL_CATEGORY -> R.string.field_category
    ExportText.COL_PARTY -> R.string.export_col_party
    ExportText.COL_ACCOUNT -> R.string.field_account
    ExportText.COL_INCOME, ExportText.TYPE_INCOME -> R.string.type_income
    ExportText.COL_EXPENSE, ExportText.TYPE_EXPENSE -> R.string.type_expense
    ExportText.COL_TRANSFER, ExportText.TYPE_TRANSFER -> R.string.type_transfer
    ExportText.COL_NOTES -> R.string.field_notes
    ExportText.COL_CREATED_BY -> R.string.export_col_entered_by
    ExportText.COL_AMOUNT -> R.string.field_amount
    ExportText.COL_SHARE -> R.string.export_col_share
    ExportText.COL_COUNT -> R.string.export_col_count
    ExportText.COL_SOURCE -> R.string.field_income_source
    ExportText.COL_OPENING -> R.string.summary_opening
    ExportText.COL_MONEY_IN -> R.string.reports_money_in
    ExportText.COL_MONEY_OUT -> R.string.reports_money_out
    ExportText.COL_CLOSING -> R.string.summary_closing
    ExportText.SHEET_SUMMARY, ExportText.SECTION_SUMMARY -> R.string.export_sheet_summary
    ExportText.SHEET_TRANSACTIONS, ExportText.SECTION_TRANSACTIONS -> R.string.export_sheet_transactions
    ExportText.SHEET_CATEGORIES -> R.string.export_sheet_categories
    ExportText.SHEET_ACCOUNTS, ExportText.SECTION_ACCOUNTS -> R.string.reports_section_accounts
    ExportText.SECTION_CHART -> R.string.reports_section_trend
    ExportText.SECTION_EXPENSE_CATEGORIES -> R.string.reports_section_expense_categories
    ExportText.SECTION_INCOME_CATEGORIES -> R.string.reports_section_income_categories
    ExportText.SECTION_SOURCES -> R.string.reports_section_sources
    ExportText.NOT_GIVEN -> R.string.reports_not_given
    ExportText.NO_TRANSACTIONS -> R.string.reports_no_data
}
