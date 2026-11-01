package com.family.finance.ui.reports

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.family.finance.R
import com.family.finance.domain.calc.DateRange
import com.family.finance.domain.calc.IncomeExpenseTotals
import com.family.finance.domain.calc.ReportingPeriod
import com.family.finance.domain.export.percentText
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.CategoryId
import com.family.finance.domain.model.CategoryKind
import com.family.finance.domain.model.Money
import com.family.finance.domain.report.AccountMovement
import com.family.finance.domain.report.Bucket
import com.family.finance.domain.usecase.TypeFilter
import com.family.finance.ui.common.PickerDates
import com.family.finance.ui.common.accountLabel
import com.family.finance.ui.common.categoryLabel
import com.family.finance.ui.common.currentLocale
import com.family.finance.ui.common.formatDate
import com.family.finance.ui.common.formatMonth
import com.family.finance.ui.common.periodLabel
import com.family.finance.ui.components.EmptyState
import com.family.finance.ui.components.IncomeExpenseStrip
import com.family.finance.ui.components.MoneyText
import com.family.finance.ui.components.SectionHeader
import com.family.finance.ui.components.isLargeFont
import com.family.finance.ui.theme.Spacing
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Filters for the entries behind a figure, opened from any row or bar. */
data class EntriesFilter(
    val range: DateRange,
    val accountId: AccountId? = null,
    val categoryId: CategoryId? = null,
    val type: TypeFilter = TypeFilter.ALL,
) {
    val period: ReportingPeriod get() = if (range.start == range.end) ReportingPeriod.Day(range.start) else ReportingPeriod.Custom(range)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    state: ReportsUiState,
    export: ExportStatus,
    snackbar: SnackbarHostState,
    actions: ReportsActions,
) {
    var showExport by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_reports)) },
                actions = {
                    IconButton(onClick = { showExport = true }, enabled = state.report != null) {
                        Icon(painterResource(R.drawable.ic_download), stringResource(R.string.action_export))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.loading) return@Scaffold
        val report = state.report ?: return@Scaffold
        val sel = state.selection
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.fabClearance),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item { PeriodBar(state, actions) }
            item { FilterBar(state, actions) }
            item {
                IncomeExpenseStrip(report.totals, Modifier.padding(horizontal = Spacing.md))
            }
            item { BalancesCard(state) }
            if (state.isEmpty) {
                item { EmptyState(R.drawable.ic_bar_chart, R.string.reports_no_data) }
                return@LazyColumn
            }
            item {
                OutlinedButton(
                    onClick = { actions.openEntries(EntriesFilter(sel.period.range, sel.accountId, sel.categoryId, sel.type)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                ) { Text("${stringResource(R.string.reports_see_entries)} (${pluralStringResource(R.plurals.reports_entry_count, report.transactions.size, report.transactions.size)})") }
            }

            item { SectionHeader(R.string.reports_section_trend) }
            item {
                ChipRow(Bucket.entries, state.bucket, { stringResource(it.labelRes()) }, actions.selectBucket, Modifier.padding(horizontal = Spacing.md))
            }
            item { BarChart(state.chart, state.bucket, onOpen = { actions.openEntries(EntriesFilter(it, sel.accountId, sel.categoryId, sel.type)) }) }
            items(state.chart.filter { !it.totals.income.isZero || !it.totals.expense.isZero }.asReversed(), key = { "b" + it.range.start }) { col ->
                BucketRow(col, state.bucket) { actions.openEntries(EntriesFilter(col.range, sel.accountId, sel.categoryId, sel.type)) }
            }

            item {
                SectionHeader(
                    if (sel.categoryKind == CategoryKind.EXPENSE) R.string.reports_section_expense_categories else R.string.reports_section_income_categories,
                )
            }
            item {
                ChipRow(
                    listOf(CategoryKind.EXPENSE, CategoryKind.INCOME), sel.categoryKind,
                    { stringResource(if (it == CategoryKind.EXPENSE) R.string.type_expense else R.string.type_income) },
                    actions.selectCategoryKind,
                    Modifier.padding(horizontal = Spacing.md),
                )
            }
            items(state.categoryRows, key = { "c" + it.categoryId?.value }) { row ->
                ShareLine(categoryLabel(state.categories[row.categoryId]), row) {
                    val type = if (sel.categoryKind == CategoryKind.EXPENSE) TypeFilter.EXPENSE else TypeFilter.INCOME
                    actions.openEntries(EntriesFilter(sel.period.range, sel.accountId, row.categoryId, type))
                }
            }

            if (state.sourceRows.isNotEmpty()) {
                item { SectionHeader(R.string.reports_section_sources) }
                items(state.sourceRows, key = { "s" + it.source }) { row ->
                    ShareLine(row.source ?: stringResource(R.string.reports_not_given), row, onClick = null)
                }
            }

            if (sel.categoryId == null && sel.type == TypeFilter.ALL) {
                item { SectionHeader(R.string.reports_section_accounts) }
                items(report.accounts, key = { "a" + it.account.id.value }) { m ->
                    AccountLine(m) { actions.openEntries(EntriesFilter(sel.period.range, m.account.id)) }
                }
            }
        }
    }

    if (showExport) {
        ExportSheet(export = export, onExport = actions.export, onDismiss = { showExport = false })
    }
}

/** Every callback of the screen, so the screen itself holds no logic. */
data class ReportsActions(
    val previous: () -> Unit,
    val next: () -> Unit,
    val selectPeriod: (ReportPeriodKind) -> Unit,
    val selectCustom: (java.time.LocalDate, java.time.LocalDate) -> Unit,
    val selectAccount: (AccountId?) -> Unit,
    val selectCategory: (CategoryId?) -> Unit,
    val selectType: (TypeFilter) -> Unit,
    val selectBucket: (Bucket) -> Unit,
    val selectCategoryKind: (CategoryKind) -> Unit,
    val openEntries: (EntriesFilter) -> Unit,
    val export: (com.family.finance.data.export.ExportFormat, com.family.finance.AppLanguage, ExportTarget) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodBar(state: ReportsUiState, actions: ReportsActions) {
    var menuOpen by remember { mutableStateOf(false) }
    var customOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = actions.previous) {
            Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.action_previous_period))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            TextButton(onClick = { menuOpen = true }) {
                Text(periodLabel(state.selection.period), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Icon(painterResource(R.drawable.ic_expand_more), stringResource(R.string.action_choose_period))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                ReportPeriodKind.entries.forEach { kind ->
                    DropdownMenuItem(text = { Text(stringResource(kind.labelRes())) }, onClick = { actions.selectPeriod(kind); menuOpen = false })
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.period_custom)) }, onClick = { menuOpen = false; customOpen = true })
            }
        }
        IconButton(onClick = actions.next) {
            Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.action_next_period))
        }
    }
    if (customOpen) {
        val range = state.selection.period.range
        val picker = rememberDateRangePickerState(
            initialSelectedStartDateMillis = PickerDates.toMillis(range.start),
            initialSelectedEndDateMillis = PickerDates.toMillis(range.end),
        )
        DatePickerDialog(
            onDismissRequest = { customOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = picker.selectedStartDateMillis
                        if (start != null) {
                            val from = PickerDates.fromMillis(start)
                            actions.selectCustom(from, picker.selectedEndDateMillis?.let(PickerDates::fromMillis) ?: from)
                        }
                        customOpen = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { customOpen = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) {
            DateRangePicker(state = picker, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun FilterBar(state: ReportsUiState, actions: ReportsActions) {
    val sel = state.selection
    Column(Modifier.padding(horizontal = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            MenuChip(
                label = sel.accountId?.let { accountLabel(state.accounts[it]) } ?: stringResource(R.string.reports_all_accounts),
                selected = sel.accountId != null,
                options = listOf<AccountId?>(null) + state.accountChoices.map { it.id },
                optionLabel = { id -> id?.let { accountLabel(state.accounts[it]) } ?: stringResource(R.string.reports_all_accounts) },
                onSelect = actions.selectAccount,
            )
            MenuChip(
                label = sel.categoryId?.let { categoryLabel(state.categories[it]) } ?: stringResource(R.string.reports_all_categories),
                selected = sel.categoryId != null,
                options = listOf<CategoryId?>(null) + state.categoryChoices.map { it.id },
                optionLabel = { id -> id?.let { categoryLabel(state.categories[it]) } ?: stringResource(R.string.reports_all_categories) },
                onSelect = actions.selectCategory,
            )
        }
        ChipRow(TypeFilter.entries, sel.type, { stringResource(it.labelRes()) }, actions.selectType)
    }
}

@Composable
private fun <T> MenuChip(label: String, selected: Boolean, options: List<T>, optionLabel: @Composable (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected,
            onClick = { open = true },
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon = { Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = { onSelect(option); open = false })
            }
        }
    }
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
        }
    }
}

@Composable
private fun BalancesCard(state: ReportsUiState) {
    val report = state.report ?: return
    Card(Modifier.fillMaxWidth().padding(horizontal = Spacing.md)) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            val b = report.balances
            if (b == null) {
                Text(stringResource(R.string.reports_balances_hidden), style = MaterialTheme.typography.bodySmall)
            } else {
                AmountLine(R.string.summary_opening, b.opening)
                if (!b.openingAdded.isZero) AmountLine(R.string.reports_opening_added, b.openingAdded)
                if (!b.transfersIn.isZero) AmountLine(R.string.reports_transfers_in, b.transfersIn)
                if (!b.transfersOut.isZero) AmountLine(R.string.reports_transfers_out, b.transfersOut)
                AmountLine(R.string.summary_closing, b.closing)
            }
            if (!report.loanReceived.isZero) AmountLine(R.string.reports_loans_received, report.loanReceived)
            if (!report.loanRepaid.isZero) AmountLine(R.string.reports_loans_repaid, report.loanRepaid)
        }
    }
}

@Composable
private fun AmountLine(@StringRes label: Int, amount: Money) {
    if (isLargeFont()) {
        Column(Modifier.fillMaxWidth()) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
            MoneyText(amount, style = MaterialTheme.typography.titleMedium)
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            MoneyText(amount, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Paired bars per day, month or year; scrolls sideways when there are many. Tap a pair to see its entries. */
@Composable
private fun BarChart(columns: List<ChartColumn>, bucket: Bucket, onOpen: (DateRange) -> Unit) {
    val income = MaterialTheme.colorScheme.tertiary
    val expense = MaterialTheme.colorScheme.error
    Card(Modifier.fillMaxWidth().padding(horizontal = Spacing.md)) {
        Column(Modifier.padding(Spacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Legend(income, stringResource(R.string.type_income))
                Legend(expense, stringResource(R.string.type_expense))
            }
            Row(
                // No fixed height: the date labels grow with the phone's font size.
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.Bottom,
            ) {
                columns.forEach { col ->
                    Column(
                        Modifier.widthIn(min = COLUMN_WIDTH).clickable { onOpen(col.range) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Row(Modifier.height(CHART_HEIGHT), verticalAlignment = Alignment.Bottom) {
                            Bar(col.incomeHeight, income)
                            Bar(col.expenseHeight, expense)
                        }
                        Text(shortLabel(col, bucket), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun Bar(basisPoints: Long, color: Color) {
    Box(
        Modifier
            .width(10.dp)
            .height(CHART_HEIGHT * (basisPoints / 10_000f))
            .background(color, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
    )
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/** Under-bar label: day number, the locale's short month name (never a cut-off word), or year. */
@Composable
private fun shortLabel(col: ChartColumn, bucket: Bucket): String = when (bucket) {
    Bucket.DAY -> col.range.start.dayOfMonth.toString()
    Bucket.MONTH -> DateTimeFormatter.ofPattern("LLL", currentLocale()).format(col.range.start)
    Bucket.YEAR -> col.range.start.year.toString()
}

@Composable
private fun bucketLabel(col: ChartColumn, bucket: Bucket): String = when (bucket) {
    Bucket.DAY -> formatDate(col.range.start)
    Bucket.MONTH -> formatMonth(YearMonth.from(col.range.start))
    Bucket.YEAR -> col.range.start.year.toString()
}

@Composable
private fun BucketRow(col: ChartColumn, bucket: Bucket, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.md, vertical = Spacing.xs)) {
        Text(bucketLabel(col, bucket), style = MaterialTheme.typography.labelLarge)
        IncomeExpenseLine(col.totals)
        HorizontalDivider(Modifier.padding(top = Spacing.xs))
    }
}

@Composable
private fun IncomeExpenseLine(totals: IncomeExpenseTotals) {
    val colors = MaterialTheme.colorScheme
    if (isLargeFont()) {
        Column {
            MoneyText(totals.income, color = colors.tertiary)
            MoneyText(totals.expense, color = colors.error)
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            MoneyText(totals.income, Modifier.weight(1f), color = colors.tertiary)
            MoneyText(totals.expense, Modifier.weight(1f), color = colors.error)
            MoneyText(totals.balance, Modifier.weight(1f))
        }
    }
}

/** A name with its amount, share of the total and a bar showing that share. */
@Composable
private fun ShareLine(name: String, row: ShareRow, onClick: (() -> Unit)?) {
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Column(
        Modifier.fillMaxWidth().then(clickable).padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (isLargeFont()) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            MoneyText(row.total, style = MaterialTheme.typography.titleMedium)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                MoneyText(row.total, style = MaterialTheme.typography.titleMedium)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(Modifier.weight(1f).height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))) {
                Box(
                    Modifier
                        .fillMaxWidth(row.shareBasisPoints / 10_000f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
                )
            }
            Text("${percentText(row.shareBasisPoints)}%", style = MaterialTheme.typography.labelMedium)
        }
        Text(
            pluralStringResource(R.plurals.reports_entry_count, row.count, row.count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AccountLine(m: AccountMovement, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = Spacing.md).clickable(onClick = onClick)) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(m.account.name, style = MaterialTheme.typography.titleSmall)
            AmountLine(R.string.summary_opening, m.opening)
            AmountLine(R.string.reports_money_in, m.income + m.transfersIn + m.openingAdded)
            AmountLine(R.string.reports_money_out, m.expense + m.transfersOut)
            AmountLine(R.string.summary_closing, m.closing)
        }
    }
}

@StringRes
private fun ReportPeriodKind.labelRes(): Int = when (this) {
    ReportPeriodKind.TODAY -> R.string.period_today
    ReportPeriodKind.THIS_WEEK -> R.string.period_this_week
    ReportPeriodKind.THIS_MONTH -> R.string.period_this_month
    ReportPeriodKind.LAST_MONTH -> R.string.period_last_month
    ReportPeriodKind.THIS_YEAR -> R.string.period_this_year
    ReportPeriodKind.LAST_YEAR -> R.string.period_last_year
}

@StringRes
private fun Bucket.labelRes(): Int = when (this) {
    Bucket.DAY -> R.string.bucket_day
    Bucket.MONTH -> R.string.bucket_month
    Bucket.YEAR -> R.string.bucket_year
}

@StringRes
private fun TypeFilter.labelRes(): Int = when (this) {
    TypeFilter.ALL -> R.string.filter_all
    TypeFilter.INCOME -> R.string.type_income
    TypeFilter.EXPENSE -> R.string.type_expense
    TypeFilter.TRANSFER -> R.string.type_transfer
}

private val CHART_HEIGHT = 140.dp
private val COLUMN_WIDTH = 32.dp
