package com.family.finance.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.family.finance.R
import com.family.finance.domain.model.Money
import com.family.finance.domain.model.MoneyFormatter
import com.family.finance.domain.model.TransactionId
import com.family.finance.ui.common.appViewModel
import com.family.finance.ui.common.periodLabel
import com.family.finance.ui.components.AmountSign
import com.family.finance.domain.calc.IncomeExpenseTotals
import com.family.finance.ui.components.EmptyState
import com.family.finance.ui.components.IncomeExpenseStrip
import com.family.finance.ui.components.MoneyText
import com.family.finance.ui.components.SectionHeader
import com.family.finance.ui.components.TransactionRow
import com.family.finance.ui.navigation.FormKind
import com.family.finance.ui.theme.Spacing

@Composable
fun HomeRoute(
    onAddTransaction: (FormKind) -> Unit,
    onOpenTransaction: (TransactionId) -> Unit,
    onSeeAll: () -> Unit,
    onAddAccount: () -> Unit,
) {
    val vm = appViewModel { c ->
        HomeViewModel(c.transactionRepository, c.accountRepository, c.categoryRepository, c::today)
    }
    val state by vm.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onPrevious = vm::previousPeriod,
        onNext = vm::nextPeriod,
        onSelectPeriod = vm::selectPeriod,
        onAddTransaction = onAddTransaction,
        onOpenTransaction = onOpenTransaction,
        onSeeAll = onSeeAll,
        onAddAccount = onAddAccount,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectPeriod: (PeriodKind) -> Unit,
    onAddTransaction: (FormKind) -> Unit,
    onOpenTransaction: (TransactionId) -> Unit,
    onSeeAll: () -> Unit,
    onAddAccount: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
        // Draw nothing until data arrives, so the dashboard never flashes before the empty state.
        if (state.loading) return@Scaffold
        if (!state.hasAccounts) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    iconRes = R.drawable.ic_account_balance_wallet,
                    messageRes = R.string.home_no_accounts,
                    actionRes = R.string.action_add_account,
                    onAction = onAddAccount,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item { BalanceCard(state.currentBalance) }
            item { PeriodSelector(state, onPrevious, onNext, onSelectPeriod) }
            state.summary?.let { summary ->
                item {
                    IncomeExpenseStrip(
                        IncomeExpenseTotals(summary.totalIncome, summary.totalExpense),
                        Modifier.padding(horizontal = Spacing.md),
                    )
                }
                item { OpeningClosingCard(opening = summary.openingBalance, closing = summary.closingBalance) }
            }
            item { QuickActions(onAddTransaction) }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(end = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionHeader(R.string.home_recent, Modifier.weight(1f))
                    if (state.recent.isNotEmpty()) {
                        TextButton(onClick = onSeeAll) { Text(stringResource(R.string.action_see_all)) }
                    }
                }
            }
            if (state.recent.isEmpty()) {
                item { EmptyState(R.drawable.ic_receipt_long, R.string.home_no_transactions) }
            }
            items(state.recent, key = { it.id.value }) { t ->
                TransactionRow(t, state.categories, state.accounts, onClick = { onOpenTransaction(t.id) })
            }
            item { Spacer(Modifier.height(Spacing.xxl)) }
        }
    }
}

@Composable
private fun BalanceCard(balance: Money) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(R.string.home_total_balance), style = MaterialTheme.typography.titleSmall)
            MoneyText(balance, style = MaterialTheme.typography.displaySmall)
            Text(stringResource(R.string.home_total_balance_hint), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PeriodSelector(
    state: HomeUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectPeriod: (PeriodKind) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.action_previous_period))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            TextButton(onClick = { menuOpen = true }) {
                Text(periodLabel(state.period), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Icon(painterResource(R.drawable.ic_expand_more), stringResource(R.string.action_choose_period))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                PeriodKind.entries.forEach { kind ->
                    DropdownMenuItem(
                        text = { Text(stringResource(kind.labelRes())) },
                        onClick = { onSelectPeriod(kind); menuOpen = false },
                    )
                }
            }
        }
        IconButton(onClick = onNext) {
            Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.action_next_period))
        }
    }
}

@StringRes
private fun PeriodKind.labelRes(): Int = when (this) {
    PeriodKind.TODAY -> R.string.period_today
    PeriodKind.THIS_WEEK -> R.string.period_this_week
    PeriodKind.THIS_MONTH -> R.string.period_this_month
    PeriodKind.LAST_MONTH -> R.string.period_last_month
    PeriodKind.THIS_YEAR -> R.string.period_this_year
}

@Composable
private fun OpeningClosingCard(opening: Money, closing: Money) {
    Card(Modifier.fillMaxWidth().padding(horizontal = Spacing.md)) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SummaryLine(R.string.summary_opening, opening)
            SummaryLine(R.string.summary_closing, closing)
        }
    }
}

@Composable
private fun SummaryLine(
    @StringRes labelRes: Int,
    amount: Money,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    sign: AmountSign = AmountSign.NONE,
) {
    val label = stringResource(labelRes)
    val spoken = "$label ${MoneyFormatter.format(amount)}"
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        MoneyText(amount, color = color, sign = sign, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun QuickActions(onAdd: (FormKind) -> Unit) = Column {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FilledTonalButton(onClick = { onAdd(FormKind.INCOME) }, modifier = Modifier.weight(1f)) {
            Icon(painterResource(R.drawable.ic_south_west), contentDescription = null)
            Text(stringResource(R.string.action_add_income), Modifier.padding(start = Spacing.xs))
        }
        FilledTonalButton(onClick = { onAdd(FormKind.EXPENSE) }, modifier = Modifier.weight(1f)) {
            Icon(painterResource(R.drawable.ic_north_east), contentDescription = null)
            Text(stringResource(R.string.action_add_expense), Modifier.padding(start = Spacing.xs))
        }
    }
    OutlinedButton(
        onClick = { onAdd(FormKind.TRANSFER) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
    ) {
        Icon(painterResource(R.drawable.ic_swap_horiz), contentDescription = null)
        Text(stringResource(R.string.action_transfer), Modifier.padding(start = Spacing.xs))
    }
}
