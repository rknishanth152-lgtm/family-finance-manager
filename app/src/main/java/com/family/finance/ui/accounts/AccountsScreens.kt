package com.family.finance.ui.accounts

import androidx.compose.foundation.layout.PaddingValues
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.family.finance.R
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.AccountType
import com.family.finance.domain.validation.Field
import com.family.finance.ui.common.accountTypeLabel
import com.family.finance.ui.common.appViewModel
import com.family.finance.ui.common.messageFor
import com.family.finance.ui.components.ChoiceChips
import com.family.finance.ui.components.DateField
import com.family.finance.ui.components.EmptyState
import com.family.finance.ui.components.MoneyText
import com.family.finance.ui.components.SectionHeader
import com.family.finance.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsRoute(onBack: () -> Unit, onAdd: () -> Unit, onEdit: (AccountId) -> Unit) {
    val vm = appViewModel { c -> AccountsViewModel(c.accountRepository, c.transactionRepository, c.accountService, c::today) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.more_accounts)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.action_add_account)) },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = Spacing.fabClearance)) {
            item {
                Card(Modifier.fillMaxWidth().padding(Spacing.md)) {
                    Column(Modifier.padding(Spacing.md)) {
                        Text(stringResource(R.string.home_total_balance), style = MaterialTheme.typography.titleSmall)
                        MoneyText(state.combined, style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }
            if (!state.loading && state.active.isEmpty() && state.archived.isEmpty()) {
                item { EmptyState(R.drawable.ic_account_balance_wallet, R.string.accounts_empty) }
            }
            items(state.active, key = { it.account.id.value }) { row ->
                AccountListItem(row, onClick = { onEdit(row.account.id) }) {
                    ArchiveButton(R.drawable.ic_archive, R.string.action_archive) { vm.setActive(row.account.id, false) }
                }
            }
            if (state.archived.isNotEmpty()) {
                item { SectionHeader(R.string.accounts_archived) }
                items(state.archived, key = { it.account.id.value }) { row ->
                    AccountListItem(row, onClick = { onEdit(row.account.id) }) {
                        ArchiveButton(R.drawable.ic_unarchive, R.string.action_restore) { vm.setActive(row.account.id, true) }
                    }
                }
            }
        }
    }
}

/** Icon-only so long Tamil words don't squeeze the balance; long-press shows the label. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveButton(@DrawableRes icon: Int, @StringRes label: Int, onClick: () -> Unit) {
    val text = stringResource(label)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick) { Icon(painterResource(icon), contentDescription = text) }
    }
}

@Composable
private fun AccountListItem(row: AccountRow, onClick: () -> Unit, action: @Composable () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(row.account.name) },
        supportingContent = {
            Column {
                Text(stringResource(accountTypeLabel(row.account.type)))
                MoneyText(row.balance, style = MaterialTheme.typography.titleMedium)
            }
        },
        trailingContent = action,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditRoute(editId: AccountId?, onDone: () -> Unit) {
    val vm = appViewModel(key = "account-${editId?.value}") { c -> AccountEditViewModel(editId, c.accountRepository, c.accountService, c::today) }
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(s.saved) { if (s.saved) onDone() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (s.isEdit) R.string.account_edit_title else R.string.action_add_account)) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_close)) }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = vm::save,
                enabled = !s.saving && s.loaded,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(Spacing.md),
            ) { Text(stringResource(R.string.action_save)) }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).imePadding().padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            val nameError = s.errors.messageFor(Field.NAME)
            OutlinedTextField(
                value = s.name,
                onValueChange = { v -> vm.edit { it.copy(name = v) } },
                label = { Text(stringResource(R.string.field_account_name)) },
                singleLine = true,
                isError = nameError != null,
                supportingText = nameError?.let { { Text(stringResource(it)) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.field_account_type), style = MaterialTheme.typography.titleSmall)
            ChoiceChips(
                options = AccountType.entries,
                selected = s.type,
                label = { stringResource(accountTypeLabel(it)) },
                onSelect = { t -> vm.edit { it.copy(type = t) } },
            )
            val balanceError = s.errors.messageFor(Field.OPENING_BALANCE)
            OutlinedTextField(
                value = s.openingBalanceText,
                onValueChange = { v -> vm.edit { it.copy(openingBalanceText = v) } },
                label = { Text(stringResource(R.string.field_opening_balance)) },
                prefix = { Text("₹") },
                singleLine = true,
                isError = balanceError != null,
                supportingText = { Text(stringResource(balanceError ?: R.string.field_opening_balance_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            DateField(
                date = s.openingBalanceDate,
                onDateChange = { d -> vm.edit { it.copy(openingBalanceDate = d) } },
                labelRes = R.string.field_opening_date,
                errorRes = s.errors.messageFor(Field.DATE),
            )
        }
    }
}
