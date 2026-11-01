package com.family.finance.ui.transactions

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.family.finance.R
import com.family.finance.domain.model.Category
import com.family.finance.domain.model.CategoryId
import com.family.finance.domain.model.TransactionId
import com.family.finance.domain.usecase.SortOrder
import com.family.finance.domain.usecase.TypeFilter
import com.family.finance.ui.common.appViewModel
import com.family.finance.ui.common.categoryLabel
import com.family.finance.ui.components.ChoiceChips
import com.family.finance.ui.components.DateField
import com.family.finance.ui.components.EmptyState
import com.family.finance.ui.components.TransactionRow
import com.family.finance.ui.theme.Spacing

@Composable
fun TransactionsRoute(onOpenTransaction: (TransactionId) -> Unit) {
    val vm = appViewModel { c -> TransactionsViewModel(c.transactionRepository, c.accountRepository, c.categoryRepository) }
    val state by vm.uiState.collectAsStateWithLifecycle()

    // Localized category names, so text search matches what is on screen.
    val names: Map<CategoryId, String> = state.categories.mapValues { (_, c) -> categoryLabel(c) }
    LaunchedEffect(names) { vm.setCategoryNames(names) }

    TransactionsScreen(
        state = state,
        onFilter = vm::updateFilter,
        onClearFilters = vm::clearFilters,
        onOpenTransaction = onOpenTransaction,
    )
}

@StringRes
private fun TypeFilter.labelRes(): Int = when (this) {
    TypeFilter.ALL -> R.string.filter_all
    TypeFilter.INCOME -> R.string.type_income
    TypeFilter.EXPENSE -> R.string.type_expense
    TypeFilter.TRANSFER -> R.string.type_transfer
}

@StringRes
private fun SortOrder.labelRes(): Int = when (this) {
    SortOrder.DATE_DESC -> R.string.sort_newest
    SortOrder.DATE_ASC -> R.string.sort_oldest
    SortOrder.AMOUNT_DESC -> R.string.sort_amount_high
    SortOrder.AMOUNT_ASC -> R.string.sort_amount_low
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    state: TransactionsUiState,
    onFilter: ((FilterInput) -> FilterInput) -> Unit,
    onClearFilters: () -> Unit,
    onOpenTransaction: (TransactionId) -> Unit,
) {
    var showFilters by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val f = state.filter

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_transactions)) },
                actions = {
                    IconButton(onClick = { sortMenu = true }) {
                        Icon(painterResource(R.drawable.ic_sort), stringResource(R.string.action_sort))
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        SortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(stringResource(order.labelRes())) },
                                leadingIcon = if (order == f.sort) {
                                    { Icon(painterResource(R.drawable.ic_check), contentDescription = null) }
                                } else {
                                    null
                                },
                                onClick = {
                                    onFilter { it.copy(sort = order) }
                                    sortMenu = false
                                },
                            )
                        }
                    }
                    IconButton(onClick = { showFilters = true }) {
                        BadgedBox(badge = { if (f.activeFilterCount > 0) Badge { Text("${f.activeFilterCount}") } }) {
                            Icon(painterResource(R.drawable.ic_filter_list), stringResource(R.string.action_filters))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryScrollableTabRow(selectedTabIndex = f.type.ordinal, edgePadding = Spacing.md) {
                TypeFilter.entries.forEach { type ->
                    Tab(
                        selected = f.type == type,
                        onClick = { onFilter { it.copy(type = type) } },
                        text = { Text(stringResource(type.labelRes())) },
                    )
                }
            }
            OutlinedTextField(
                value = f.text,
                onValueChange = { v -> onFilter { it.copy(text = v) } },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                trailingIcon = if (f.text.isNotEmpty()) {
                    {
                        IconButton(onClick = { onFilter { it.copy(text = "") } }) {
                            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_clear_search))
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            )
            if (!state.loading && state.items.isEmpty()) {
                EmptyState(R.drawable.ic_receipt_long, R.string.transactions_empty)
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.items, key = { it.id.value }) { t ->
                        TransactionRow(t, state.categories, state.accounts, onClick = { onOpenTransaction(t.id) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }) {
            FilterSheet(state, onFilter, onClearFilters)
        }
    }
}

@Composable
private fun FilterSheet(
    state: TransactionsUiState,
    onFilter: ((FilterInput) -> FilterInput) -> Unit,
    onClearFilters: () -> Unit,
) {
    val f = state.filter
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.action_filters),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClearFilters) { Text(stringResource(R.string.action_clear_filters)) }
        }
        DateField(f.from, { d -> onFilter { it.copy(from = d) } }, R.string.filter_from_date)
        DateField(f.to, { d -> onFilter { it.copy(to = d) } }, R.string.filter_to_date)

        Text(stringResource(R.string.field_account), style = MaterialTheme.typography.titleSmall)
        val accounts = state.accounts.values.sortedBy { it.name.lowercase() }
        ChoiceChips(
            options = accounts,
            selected = accounts.firstOrNull { it.id == f.accountId },
            label = { it.name },
            onSelect = { a -> onFilter { it.copy(accountId = if (it.accountId == a.id) null else a.id) } },
        )

        Text(stringResource(R.string.field_category), style = MaterialTheme.typography.titleSmall)
        val categories: List<Category> = state.categories.values.filter { !it.isArchived || it.id == f.categoryId }
        ChoiceChips(
            options = categories,
            selected = categories.firstOrNull { it.id == f.categoryId },
            label = { categoryLabel(it) },
            onSelect = { c -> onFilter { it.copy(categoryId = if (it.categoryId == c.id) null else c.id) } },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedTextField(
                value = f.minAmountText,
                onValueChange = { v -> onFilter { it.copy(minAmountText = v) } },
                label = { Text(stringResource(R.string.filter_min_amount)) },
                prefix = { Text("₹") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = f.maxAmountText,
                onValueChange = { v -> onFilter { it.copy(maxAmountText = v) } },
                label = { Text(stringResource(R.string.filter_max_amount)) },
                prefix = { Text("₹") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
