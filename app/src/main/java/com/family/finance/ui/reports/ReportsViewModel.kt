package com.family.finance.ui.reports

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.finance.AppLanguage
import com.family.finance.data.export.ExportFormat
import com.family.finance.data.export.ExportRequest
import com.family.finance.data.export.ExportedFile
import com.family.finance.domain.calc.DateRange
import com.family.finance.domain.calc.IncomeExpenseTotals
import com.family.finance.domain.calc.ReportingPeriod
import com.family.finance.domain.model.Account
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.Category
import com.family.finance.domain.model.CategoryId
import com.family.finance.domain.model.CategoryKind
import com.family.finance.domain.model.Member
import com.family.finance.domain.model.Money
import com.family.finance.domain.model.UserId
import com.family.finance.domain.report.Bucket
import com.family.finance.domain.report.CategoryTotal
import com.family.finance.domain.report.Report
import com.family.finance.domain.report.ReportFilter
import com.family.finance.domain.report.Reports
import com.family.finance.domain.repository.AccountRepository
import com.family.finance.domain.repository.CategoryRepository
import com.family.finance.domain.repository.TransactionRepository
import com.family.finance.domain.usecase.TypeFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

enum class ReportPeriodKind { TODAY, THIS_WEEK, THIS_MONTH, LAST_MONTH, THIS_YEAR, LAST_YEAR, ALL_TIME }

/** What the person has chosen on the Reports screen. A null [bucket] means "pick by range length". */
data class ReportSelection(
    val period: ReportingPeriod,
    val accountId: AccountId? = null,
    val categoryId: CategoryId? = null,
    val type: TypeFilter = TypeFilter.ALL,
    val bucket: Bucket? = null,
    val categoryKind: CategoryKind = CategoryKind.EXPENSE,
) {
    val filter: ReportFilter get() = ReportFilter(period.range, accountId, categoryId, type)
}

/** One chart bar pair; heights are hundredths of a percent of the tallest bar. */
data class ChartColumn(val range: DateRange, val totals: IncomeExpenseTotals, val incomeHeight: Long, val expenseHeight: Long)

data class ShareRow(val categoryId: CategoryId?, val source: String?, val total: Money, val shareBasisPoints: Long, val count: Int)

data class ReportsUiState(
    val loading: Boolean = true,
    val selection: ReportSelection,
    val report: Report? = null,
    val bucket: Bucket = Bucket.DAY,
    val chart: List<ChartColumn> = emptyList(),
    val categoryRows: List<ShareRow> = emptyList(),
    val sourceRows: List<ShareRow> = emptyList(),
    val accounts: Map<AccountId, Account> = emptyMap(),
    val categories: Map<CategoryId, Category> = emptyMap(),
    /** Choices for the filters: active ones first, then archived (they still have history). */
    val accountChoices: List<Account> = emptyList(),
    val categoryChoices: List<Category> = emptyList(),
    /** Names of family members, for "Entered by" in exports. */
    val people: Map<UserId, String> = emptyMap(),
) {
    val isEmpty: Boolean get() = report?.transactions?.isEmpty() ?: true
}

sealed interface ExportStatus {
    data object Idle : ExportStatus
    data object Working : ExportStatus
    /** Ready to hand to the system save dialog or share sheet. */
    data class Ready(val file: ExportedFile, val target: ExportTarget) : ExportStatus
    data object Saved : ExportStatus
    data object Failed : ExportStatus
}

enum class ExportTarget { SAVE, SHARE }

class ReportsViewModel(
    transactions: TransactionRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    members: Flow<List<Member>> = flowOf(emptyList()),
    private val familyName: () -> String,
    private val today: () -> LocalDate,
    private val exportFile: suspend (ExportRequest) -> ExportedFile,
    private val saveFile: suspend (File, Uri) -> Unit,
) : ViewModel() {

    private val selection = MutableStateFlow(ReportSelection(ReportingPeriod.thisMonth(today())))
    /** Dates of the oldest and newest entries, for "All time". */
    private var earliest: LocalDate? = null
    private var latest: LocalDate? = null

    private val _export = MutableStateFlow<ExportStatus>(ExportStatus.Idle)
    val export: StateFlow<ExportStatus> = _export.asStateFlow()

    val uiState: StateFlow<ReportsUiState> = combine(
        selection, transactions.observeAll(), accounts.observeAll(), categories.observeAll(), members,
    ) { sel, txns, accts, cats, mems ->
        earliest = txns.minOfOrNull { it.date }
        latest = txns.maxOfOrNull { it.date }
        val report = Reports.build(sel.filter, txns, accts, cats)
        val bucket = sel.bucket ?: Reports.defaultBucket(sel.period.range)
        ReportsUiState(
            loading = false,
            selection = sel,
            report = report,
            bucket = bucket,
            chart = chart(report, bucket),
            categoryRows = shareRows(if (sel.categoryKind == CategoryKind.EXPENSE) report.expenseByCategory else report.incomeByCategory,
                if (sel.categoryKind == CategoryKind.EXPENSE) report.totals.expense else report.totals.income),
            sourceRows = report.incomeBySource.map {
                ShareRow(null, it.source, it.total, Reports.shareBasisPoints(it.total, report.totals.income), it.count)
            },
            accounts = accts.associateBy { it.id },
            categories = cats.associateBy { it.id },
            accountChoices = accts.sortedWith(compareByDescending<Account> { it.isActive }.thenBy { it.name }),
            categoryChoices = cats.sortedWith(compareBy<Category> { it.kind }.thenBy { it.isArchived }),
            people = mems.associate { it.uid to it.displayName },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState(selection = selection.value))

    private fun chart(report: Report, bucket: Bucket): List<ChartColumn> {
        val buckets = report.buckets(bucket)
        val max = buckets.maxOfOrNull { maxOf(it.totals.income, it.totals.expense) } ?: Money.ZERO
        return buckets.map {
            ChartColumn(it.range, it.totals, Reports.shareBasisPoints(it.totals.income, max), Reports.shareBasisPoints(it.totals.expense, max))
        }
    }

    private fun shareRows(totals: List<CategoryTotal>, whole: Money) =
        totals.map { ShareRow(it.categoryId, null, it.total, Reports.shareBasisPoints(it.total, whole), it.count) }

    fun previousPeriod() = selection.update { it.copy(period = it.period.previous()) }
    fun nextPeriod() = selection.update { it.copy(period = it.period.next()) }

    fun selectPeriod(kind: ReportPeriodKind) {
        val d = today()
        val period = when (kind) {
            ReportPeriodKind.TODAY -> ReportingPeriod.today(d)
            ReportPeriodKind.THIS_WEEK -> ReportingPeriod.thisWeek(d)
            ReportPeriodKind.THIS_MONTH -> ReportingPeriod.thisMonth(d)
            ReportPeriodKind.LAST_MONTH -> ReportingPeriod.lastMonth(d)
            ReportPeriodKind.THIS_YEAR -> ReportingPeriod.thisYear(d)
            ReportPeriodKind.LAST_YEAR -> ReportingPeriod.lastYear(d)
            // From the oldest entry to today (e.g. to check imported totals against the old app).
            ReportPeriodKind.ALL_TIME -> ReportingPeriod.Custom(DateRange(minOf(earliest ?: d, d), maxOf(latest ?: d, d)))
        }
        selection.update { it.copy(period = period, bucket = null) }
    }

    /** Dates picked in either order are accepted. */
    fun selectCustom(from: LocalDate, to: LocalDate) {
        val range = if (to.isBefore(from)) DateRange(to, from) else DateRange(from, to)
        selection.update { it.copy(period = ReportingPeriod.Custom(range), bucket = null) }
    }

    fun selectAccount(id: AccountId?) = selection.update { it.copy(accountId = id) }
    fun selectCategory(id: CategoryId?) = selection.update { it.copy(categoryId = id) }
    fun selectType(type: TypeFilter) = selection.update { it.copy(type = type) }
    fun selectBucket(bucket: Bucket) = selection.update { it.copy(bucket = bucket) }
    fun selectCategoryKind(kind: CategoryKind) = selection.update { it.copy(categoryKind = kind) }

    fun startExport(format: ExportFormat, language: AppLanguage, target: ExportTarget) {
        val state = uiState.value
        val report = state.report ?: return
        if (_export.value == ExportStatus.Working) return
        _export.value = ExportStatus.Working
        viewModelScope.launch {
            _export.value = runCatching {
                exportFile(
                    ExportRequest(report, format, language, familyName(), today(), state.accounts, state.categories, state.people),
                )
            }.fold({ ExportStatus.Ready(it, target) }, { ExportStatus.Failed })
        }
    }

    /** Called with the place the person chose in the system save dialog (null if they cancelled). */
    fun saveTo(uri: Uri?) {
        val ready = _export.value as? ExportStatus.Ready ?: return
        if (uri == null) {
            _export.value = ExportStatus.Idle
            return
        }
        _export.value = ExportStatus.Working
        viewModelScope.launch {
            _export.value = runCatching { saveFile(ready.file.file, uri) }.fold({ ExportStatus.Saved }, { ExportStatus.Failed })
        }
    }

    fun exportHandled() {
        _export.value = ExportStatus.Idle
    }
}
