package com.family.finance.ui.calendar

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Card
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.family.finance.R
import com.family.finance.domain.calc.DayTotal
import com.family.finance.domain.model.MoneyFormatter
import com.family.finance.domain.model.TransactionId
import com.family.finance.ui.common.appViewModel
import com.family.finance.ui.common.currentLocale
import com.family.finance.ui.common.formatDate
import com.family.finance.ui.common.formatMonth
import com.family.finance.ui.components.AmountSign
import com.family.finance.ui.components.EmptyState
import com.family.finance.ui.components.isLargeFont
import com.family.finance.ui.components.MoneyText
import com.family.finance.ui.components.TransactionRow
import com.family.finance.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

@Composable
fun CalendarRoute(onOpenTransaction: (TransactionId) -> Unit) {
    val vm = appViewModel { c -> CalendarViewModel(c.transactionRepository, c.accountRepository, c.categoryRepository, c::today) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    CalendarScreen(state, vm::previousMonth, vm::nextMonth, vm::select, onOpenTransaction)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    state: CalendarUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit,
    onOpenTransaction: (TransactionId) -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_calendar)) }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = Spacing.fabClearance)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrevious) {
                        Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.action_previous_month))
                    }
                    Text(
                        formatMonth(state.month),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onNext) {
                        Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.action_next_month))
                    }
                }
            }
            item { MonthGrid(state, onSelect) }
            item { DaySummary(state.selected, state.totals[state.selected]) }
            if (state.dayTransactions.isEmpty()) {
                item { EmptyState(R.drawable.ic_calendar_month, R.string.calendar_no_transactions) }
            }
            items(state.dayTransactions, key = { it.id.value }) { t ->
                TransactionRow(t, state.categories, state.accounts, onClick = { onOpenTransaction(t.id) }, showDate = false)
            }
        }
    }
}

@Composable
private fun MonthGrid(state: CalendarUiState, onSelect: (LocalDate) -> Unit) {
    val locale = currentLocale()
    val first = state.month.atDay(1)
    // Monday-first grid; leading blanks before the 1st.
    val leading = first.dayOfWeek.value - DayOfWeek.MONDAY.value
    val cells: List<LocalDate?> = List(leading) { null } + (1..state.month.lengthOfMonth()).map { state.month.atDay(it) }
    Column(Modifier.padding(horizontal = Spacing.sm)) {
        Row(Modifier.fillMaxWidth()) {
            // Large fonts: one-letter weekday names (as on printed Tamil calendars), and shrink if still needed.
            val nameStyle = if (isLargeFont()) TextStyle.NARROW else TextStyle.SHORT
            val headerStyle = MaterialTheme.typography.labelSmall
            DayOfWeek.entries.forEach { dow ->
                BasicText(
                    dow.getDisplayName(nameStyle, locale),
                    modifier = Modifier.weight(1f),
                    style = headerStyle.copy(color = LocalContentColor.current, textAlign = TextAlign.Center),
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = headerStyle.fontSize * 0.6f, maxFontSize = headerStyle.fontSize),
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp)) {
                        if (date != null) DayCell(date, state, onSelect)
                    }
                }
                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, state: CalendarUiState, onSelect: (LocalDate) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val total = state.totals[date]
    val isSelected = date == state.selected
    val description = if (total != null) {
        pluralStringResource(R.plurals.calendar_day_with_activity, total.transactionCount, formatDate(date), total.transactionCount)
    } else {
        formatDate(date)
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) colors.primaryContainer else colors.surface,
        modifier = Modifier
            .fillMaxSize()
            .then(if (date == state.today) Modifier.border(1.dp, colors.primary, RoundedCornerShape(8.dp)) else Modifier)
            .clickable(role = Role.Button) { onSelect(date) }
            .semantics {
                contentDescription = description
                selected = isSelected
            },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("${date.dayOfMonth}", style = MaterialTheme.typography.bodyMedium)
            if (total != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (total.income.isPositive) Dot(colors.tertiary)
                    if (total.expense.isPositive) Dot(colors.error)
                    if (!total.income.isPositive && !total.expense.isPositive) Dot(colors.secondary)
                }
            }
        }
    }
}

@Composable
private fun Dot(color: androidx.compose.ui.graphics.Color) {
    Surface(shape = CircleShape, color = color, modifier = Modifier.size(6.dp)) {}
}

@Composable
private fun DaySummary(date: LocalDate, total: DayTotal?) {
    Card(Modifier.fillMaxWidth().padding(Spacing.md)) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(formatDate(date), style = MaterialTheme.typography.titleMedium)
            val income = total?.income ?: com.family.finance.domain.model.Money.ZERO
            val expense = total?.expense ?: com.family.finance.domain.model.Money.ZERO
            val net = total?.net ?: com.family.finance.domain.model.Money.ZERO
            DayLine(R.string.summary_income, income, MaterialTheme.colorScheme.tertiary, AmountSign.PLUS)
            DayLine(R.string.summary_expense, expense, MaterialTheme.colorScheme.error, AmountSign.MINUS)
            DayLine(R.string.calendar_net, net, MaterialTheme.colorScheme.onSurface, AmountSign.NONE)
        }
    }
}

@Composable
private fun DayLine(
    labelRes: Int,
    amount: com.family.finance.domain.model.Money,
    color: androidx.compose.ui.graphics.Color,
    sign: AmountSign,
) {
    val label = stringResource(labelRes)
    val spoken = "$label ${MoneyFormatter.format(amount)}"
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = spoken }) {
        Text(label, Modifier.weight(1f))
        MoneyText(amount, color = color, sign = sign)
    }
}
