package com.family.finance.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.family.finance.R
import com.family.finance.domain.calc.IncomeExpenseTotals
import com.family.finance.domain.model.Account
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.Category
import com.family.finance.domain.model.CategoryId
import com.family.finance.domain.model.Transaction
import com.family.finance.domain.model.TransactionId
import com.family.finance.domain.repository.AccountRepository
import com.family.finance.domain.repository.CategoryRepository
import com.family.finance.domain.repository.TransactionRepository
import com.family.finance.domain.usecase.SortOrder
import com.family.finance.domain.usecase.TransactionQuery
import com.family.finance.ui.common.appViewModel
import com.family.finance.ui.common.periodLabel
import com.family.finance.domain.calc.ReportingPeriod
import com.family.finance.ui.components.EmptyState
import com.family.finance.ui.components.IncomeExpenseStrip
import com.family.finance.ui.components.TransactionRow
import com.family.finance.ui.theme.Spacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ReportEntriesUiState(
    val loading: Boolean = true,
    val items: List<Transaction> = emptyList(),
    val totals: IncomeExpenseTotals = IncomeExpenseTotals.ZERO,
    val accounts: Map<AccountId, Account> = emptyMap(),
    val categories: Map<CategoryId, Category> = emptyMap(),
)

/** The entries behind one figure of a report, newest first, with their own totals. */
class ReportEntriesViewModel(
    filter: EntriesFilter,
    transactions: TransactionRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
) : ViewModel() {
    private val query = TransactionQuery(
        type = filter.type, range = filter.range, accountId = filter.accountId, categoryId = filter.categoryId, sort = SortOrder.DATE_DESC,
    )

    val uiState: StateFlow<ReportEntriesUiState> = combine(
        transactions.observeAll(), accounts.observeAll(), categories.observeAll(),
    ) { txns, accts, cats ->
        val items = query.apply(txns)
        ReportEntriesUiState(false, items, IncomeExpenseTotals.of(items), accts.associateBy { it.id }, cats.associateBy { it.id })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportEntriesUiState())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportEntriesRoute(filter: EntriesFilter, onBack: () -> Unit, onOpenTransaction: (TransactionId) -> Unit) {
    val vm = appViewModel(key = filter.toString()) { c ->
        ReportEntriesViewModel(filter, c.transactionRepository, c.accountRepository, c.categoryRepository)
    }
    val state by vm.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(periodLabel(filter.period)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            item { IncomeExpenseStrip(state.totals, Modifier.padding(horizontal = Spacing.md)) }
            if (state.items.isEmpty()) item { EmptyState(R.drawable.ic_receipt_long, R.string.reports_no_data) }
            items(state.items, key = { it.id.value }) { t ->
                TransactionRow(t, state.categories, state.accounts, onClick = { onOpenTransaction(t.id) })
            }
        }
    }
}
