package com.family.finance.ui.form

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.family.finance.R
import com.family.finance.domain.model.Account
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.TransactionId
import com.family.finance.domain.validation.Field
import com.family.finance.domain.validation.ValidationError
import com.family.finance.ui.common.appViewModel
import com.family.finance.ui.common.categoryLabel
import com.family.finance.ui.common.messageFor
import com.family.finance.ui.components.ChoiceChips
import com.family.finance.ui.components.DateField
import com.family.finance.ui.components.FieldError
import com.family.finance.ui.navigation.FormKind
import com.family.finance.ui.theme.Spacing

@Composable
fun TransactionFormRoute(
    kind: FormKind,
    editId: TransactionId?,
    onClose: () -> Unit,
    onViewTransaction: (TransactionId) -> Unit,
    onAddAccount: () -> Unit,
) {
    val vm = appViewModel(key = "form-${kind.name}-${editId?.value}") { c ->
        TransactionFormViewModel(
            kind, editId, c.transactionService, c.transactionRepository, c.accountRepository,
            c.categoryRepository, c.entryDefaults, c::today,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val options by vm.options.collectAsStateWithLifecycle()
    TransactionFormScreen(
        state = state,
        options = options,
        onEdit = vm::edit,
        onToggleMore = vm::toggleMore,
        onSave = vm::save,
        onAddAnother = vm::addAnother,
        onClose = onClose,
        onViewTransaction = onViewTransaction,
        onAddAccount = onAddAccount,
    )
}

@StringRes
private fun titleRes(kind: FormKind, isEdit: Boolean): Int = when (kind) {
    FormKind.INCOME -> if (isEdit) R.string.form_edit_income else R.string.action_add_income
    FormKind.EXPENSE -> if (isEdit) R.string.form_edit_expense else R.string.action_add_expense
    FormKind.TRANSFER -> if (isEdit) R.string.form_edit_transfer else R.string.action_transfer
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFormScreen(
    state: FormState,
    options: FormOptions,
    onEdit: ((FormFields) -> FormFields) -> Unit,
    onToggleMore: () -> Unit,
    onSave: () -> Unit,
    onAddAnother: () -> Unit,
    onClose: () -> Unit,
    onViewTransaction: (TransactionId) -> Unit,
    onAddAccount: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes(state.kind, state.isEdit))) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_close))
                    }
                },
            )
        },
        bottomBar = {
            if (state.savedId == null) {
                Button(
                    onClick = onSave,
                    enabled = !state.saving && state.loaded,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(Spacing.md),
                ) { Text(stringResource(R.string.action_save)) }
            }
        },
    ) { padding ->
        val saved = state.savedId
        if (saved != null) {
            SavedPanel(
                modifier = Modifier.padding(padding),
                onView = { onViewTransaction(saved) },
                onAddAnother = onAddAnother,
                onDone = onClose,
            )
            return@Scaffold
        }
        FormBody(state, options, onEdit, onToggleMore, onAddAccount, Modifier.padding(padding))
    }
}

@Composable
private fun FormBody(
    state: FormState,
    options: FormOptions,
    onEdit: ((FormFields) -> FormFields) -> Unit,
    onToggleMore: () -> Unit,
    onAddAccount: () -> Unit,
    modifier: Modifier,
) {
    val f = state.fields
    val errors = state.errors
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // Amount first and largest (PRD 6.2, 14.3).
        OutlinedTextField(
            value = f.amountText,
            onValueChange = { v -> onEdit { it.copy(amountText = v) } },
            label = { Text(stringResource(R.string.field_amount)) },
            prefix = { Text("₹") },
            textStyle = MaterialTheme.typography.headlineMedium,
            singleLine = true,
            isError = errors.messageFor(Field.AMOUNT) != null,
            supportingText = errors.messageFor(Field.AMOUNT)?.let { { Text(stringResource(it)) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.kind != FormKind.TRANSFER) {
            TextInput(
                value = f.title,
                onChange = { v -> onEdit { it.copy(title = v) } },
                labelRes = R.string.field_title,
                errorRes = errors.messageFor(Field.TITLE),
            )
            Text(stringResource(R.string.field_category), style = MaterialTheme.typography.titleSmall)
            ChoiceChips(
                options = options.categories,
                selected = options.categories.firstOrNull { it.id == f.categoryId },
                label = { categoryLabel(it) },
                onSelect = { c -> onEdit { it.copy(categoryId = c.id) } },
            )
            FieldError(errors.messageFor(Field.CATEGORY))
        }

        if (state.kind == FormKind.INCOME) {
            TextInput(
                value = f.source,
                onChange = { v -> onEdit { it.copy(source = v) } },
                labelRes = R.string.field_income_source,
                errorRes = errors.messageFor(Field.INCOME_SOURCE),
            )
        }

        if (options.accounts.isEmpty()) {
            OutlinedButton(onClick = onAddAccount, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_add_account))
            }
        }
        AccountPicker(
            labelRes = when (state.kind) {
                FormKind.INCOME -> R.string.field_to_account
                FormKind.EXPENSE -> R.string.field_from_account
                FormKind.TRANSFER -> R.string.field_from_account
            },
            accounts = options.accounts,
            selected = f.accountId,
            onSelect = { id -> onEdit { it.copy(accountId = id) } },
            errorRes = errors.messageFor(Field.ACCOUNT) ?: errors.messageFor(Field.FROM_ACCOUNT),
        )
        if (state.kind == FormKind.TRANSFER) {
            AccountPicker(
                labelRes = R.string.field_to_account,
                accounts = options.accounts,
                selected = f.toAccountId,
                onSelect = { id -> onEdit { it.copy(toAccountId = id) } },
                errorRes = errors.messageFor(Field.TO_ACCOUNT),
            )
        }

        DateField(
            date = f.date,
            onDateChange = { d -> onEdit { it.copy(date = d) } },
            labelRes = R.string.field_date,
            errorRes = errors.messageFor(Field.DATE),
        )

        if (state.kind == FormKind.TRANSFER) {
            TextInput(
                value = f.notes,
                onChange = { v -> onEdit { it.copy(notes = v) } },
                labelRes = R.string.field_description,
                singleLine = false,
            )
        } else {
            TextButton(onClick = onToggleMore) {
                Icon(
                    painterResource(if (state.showMore) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                    contentDescription = null,
                )
                Text(stringResource(if (state.showMore) R.string.action_fewer_options else R.string.action_more_options))
            }
            if (state.showMore) {
                if (state.kind == FormKind.EXPENSE) {
                    TextInput(
                        value = f.merchant,
                        onChange = { v -> onEdit { it.copy(merchant = v) } },
                        labelRes = R.string.field_merchant,
                    )
                }
                TextInput(
                    value = f.notes,
                    onChange = { v -> onEdit { it.copy(notes = v) } },
                    labelRes = R.string.field_notes,
                    singleLine = false,
                )
            }
        }
        ErrorSummary(errors)
    }
}

@Composable
private fun TextInput(
    value: String,
    onChange: (String) -> Unit,
    @StringRes labelRes: Int,
    @StringRes errorRes: Int? = null,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(labelRes)) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        isError = errorRes != null,
        supportingText = errorRes?.let { { Text(stringResource(it)) } },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AccountPicker(
    @StringRes labelRes: Int,
    accounts: List<Account>,
    selected: AccountId?,
    onSelect: (AccountId) -> Unit,
    @StringRes errorRes: Int?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.titleSmall)
        ChoiceChips(
            options = accounts,
            selected = accounts.firstOrNull { it.id == selected },
            label = { it.name },
            onSelect = { onSelect(it.id) },
        )
        FieldError(errorRes)
    }
}

/** Announces that the form has errors so screen-reader users hear it (PRD 14.5). */
@Composable
private fun ErrorSummary(errors: List<ValidationError>) {
    if (errors.isEmpty()) return
    Text(
        stringResource(R.string.form_fix_errors),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun SavedPanel(
    modifier: Modifier,
    onView: () -> Unit,
    onAddAnother: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
    ) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.tertiaryContainer) {
            Icon(
                painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(Spacing.md).size(48.dp),
            )
        }
        Text(
            stringResource(R.string.form_saved),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Button(onClick = onAddAnother, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_add_another)) }
        OutlinedButton(onClick = onView, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_view_transaction)) }
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_done)) }
    }
}
