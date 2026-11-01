package com.family.finance.ui.transactions

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.family.finance.R
import com.family.finance.domain.model.Transaction
import com.family.finance.domain.model.TransactionId
import com.family.finance.ui.common.accountLabel
import com.family.finance.ui.common.appViewModel
import com.family.finance.ui.common.categoryLabel
import com.family.finance.ui.common.formatDate
import com.family.finance.ui.components.AmountSign
import com.family.finance.ui.components.EmptyState
import com.family.finance.ui.components.WarningCard
import com.family.finance.ui.common.currentLocale
import com.family.finance.domain.model.MoneyFormatter
import androidx.compose.material3.Button
import com.family.finance.ui.components.MoneyText
import com.family.finance.ui.navigation.FormKind
import com.family.finance.ui.theme.Spacing

@Composable
fun TransactionDetailRoute(
    id: TransactionId,
    onBack: () -> Unit,
    onEdit: (FormKind, TransactionId) -> Unit,
) {
    val vm = appViewModel(key = "detail-${id.value}") { c ->
        TransactionDetailViewModel(
            id, c.transactionRepository, c.accountRepository, c.categoryRepository, c.transactionService,
            c.familyRepository.observeMembers(),
        )
    }
    val state by vm.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    TransactionDetailScreen(state, onBack, onEdit, vm::delete, vm::confirmCurrent, vm::restore)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    state: DetailUiState,
    onBack: () -> Unit,
    onEdit: (FormKind, TransactionId) -> Unit,
    onConfirmDelete: () -> Unit,
    onConfirmVersion: () -> Unit = {},
    onRestore: (Transaction) -> Unit = {},
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val t = state.transaction
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (t != null) {
                        IconButton(onClick = { onEdit(t.formKind(), t.id) }) {
                            Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.action_edit))
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.action_delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (t == null) {
            if (!state.loading) {
                Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.md)) {
                    // Deleted on one phone while edited on the other: offer the edited version back.
                    if (state.competing.isNotEmpty()) ConflictCard(state, current = null, onConfirmVersion, onRestore)
                    EmptyState(R.drawable.ic_receipt_long, R.string.detail_not_found)
                }
            }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (state.competing.isNotEmpty()) ConflictCard(state, current = t, onConfirmVersion, onRestore)
            val colors = MaterialTheme.colorScheme
            when (t) {
                is Transaction.Income -> MoneyText(t.amount, style = MaterialTheme.typography.displaySmall, color = colors.tertiary, sign = AmountSign.PLUS)
                is Transaction.Expense -> MoneyText(t.amount, style = MaterialTheme.typography.displaySmall, color = colors.error, sign = AmountSign.MINUS)
                is Transaction.Transfer -> MoneyText(t.amount, style = MaterialTheme.typography.displaySmall)
            }
            DetailLine(R.string.field_type, stringResource(t.typeLabel()))
            if (t.title.isNotBlank()) DetailLine(R.string.field_title, t.title)
            DetailLine(R.string.field_date, formatDate(t.date))
            when (t) {
                is Transaction.Income -> {
                    DetailLine(R.string.field_category, categoryLabel(state.categories[t.categoryId]))
                    DetailLine(R.string.field_income_source, t.source)
                    DetailLine(R.string.field_to_account, accountLabel(state.accounts[t.accountId]))
                }
                is Transaction.Expense -> {
                    DetailLine(R.string.field_category, categoryLabel(state.categories[t.categoryId]))
                    t.merchant?.let { DetailLine(R.string.field_merchant, it) }
                    DetailLine(R.string.field_from_account, accountLabel(state.accounts[t.accountId]))
                }
                is Transaction.Transfer -> {
                    DetailLine(R.string.field_from_account, accountLabel(state.accounts[t.fromAccountId]))
                    DetailLine(R.string.field_to_account, accountLabel(state.accounts[t.toAccountId]))
                }
            }
            if (t !is Transaction.Transfer) t.notes?.let { DetailLine(R.string.field_notes, it) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_transaction_title)) },
            text = { Text(stringResource(R.string.delete_transaction_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onConfirmDelete()
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun DetailLine(@StringRes labelRes: Int, value: String) {
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

fun Transaction.formKind(): FormKind = when (this) {
    is Transaction.Income -> FormKind.INCOME
    is Transaction.Expense -> FormKind.EXPENSE
    is Transaction.Transfer -> FormKind.TRANSFER
}

@StringRes
private fun Transaction.typeLabel(): Int = when (this) {
    is Transaction.Income -> R.string.type_income
    is Transaction.Expense -> R.string.type_expense
    is Transaction.Transfer -> R.string.type_transfer
}

/**
 * Both phones saved this entry without seeing each other's change (PRD 9.4, 11.5).
 * Every version is listed; the user confirms the shown one or brings another back.
 */
@Composable
private fun ConflictCard(
    state: DetailUiState,
    current: Transaction?,
    onConfirmVersion: () -> Unit,
    onRestore: (Transaction) -> Unit,
) {
    val time = java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.SHORT)
        .withLocale(currentLocale()).withZone(java.time.ZoneId.systemDefault())
    WarningCard(
        title = stringResource(R.string.conflict_detail_title),
        message = stringResource(R.string.conflict_detail_message),
    ) {
        state.competing.forEach { entry ->
            val who = state.names[entry.editedBy] ?: stringResource(R.string.label_unknown)
            val what = entry.state?.let { MoneyFormatter.format(it.amount) + " · " + it.title.ifBlank { stringResource(R.string.type_transfer) } }
                ?: stringResource(R.string.conflict_deleted)
            Text(stringResource(R.string.conflict_version_line, who, time.format(entry.editedAt), what), style = MaterialTheme.typography.bodyMedium)
            val version = entry.state
            if (version != null && version != current) {
                TextButton(onClick = { onRestore(version) }) { Text(stringResource(R.string.action_use_this_version)) }
            }
        }
        if (current != null) {
            Button(onClick = onConfirmVersion) { Text(stringResource(R.string.action_confirm_version)) }
        }
    }
}
