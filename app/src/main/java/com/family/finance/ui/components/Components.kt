package com.family.finance.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.family.finance.R
import com.family.finance.domain.model.Account
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.Category
import com.family.finance.domain.model.CategoryId
import com.family.finance.domain.model.Money
import com.family.finance.domain.model.MoneyFormatter
import com.family.finance.domain.model.Transaction
import com.family.finance.ui.common.PickerDates
import com.family.finance.ui.common.accountLabel
import com.family.finance.ui.common.categoryLabel
import com.family.finance.ui.common.formatDate
import com.family.finance.ui.theme.Spacing
import java.time.LocalDate

enum class AmountSign { PLUS, MINUS, NONE }

@Composable
fun MoneyText(
    money: Money,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.Unspecified,
    sign: AmountSign = AmountSign.NONE,
) {
    val formatted = MoneyFormatter.format(money)
    val text = when (sign) {
        AmountSign.PLUS -> "+$formatted"
        AmountSign.MINUS -> "−$formatted"
        AmountSign.NONE -> formatted
    }
    Text(text = text, modifier = modifier, style = style, color = color, maxLines = 1)
}

@Composable
fun SectionHeader(@StringRes textRes: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .semantics { heading() },
    )
}

@Composable
fun EmptyState(
    @DrawableRes iconRes: Int,
    @StringRes messageRes: Int,
    modifier: Modifier = Modifier,
    @StringRes actionRes: Int? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(
            painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(messageRes),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionRes != null) {
            Button(onClick = onAction) { Text(stringResource(actionRes)) }
        }
    }
}

@Composable
fun TransactionRow(
    transaction: Transaction,
    categories: Map<CategoryId, Category>,
    accounts: Map<AccountId, Account>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDate: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val (iconRes, typeLabel, container, onContainer) = when (transaction) {
        is Transaction.Income -> Quad(R.drawable.ic_south_west, R.string.type_income, colors.tertiaryContainer, colors.onTertiaryContainer)
        is Transaction.Expense -> Quad(R.drawable.ic_north_east, R.string.type_expense, colors.errorContainer, colors.onErrorContainer)
        is Transaction.Transfer -> Quad(R.drawable.ic_swap_horiz, R.string.type_transfer, colors.secondaryContainer, colors.onSecondaryContainer)
    }
    val title = transaction.title.ifBlank { stringResource(R.string.type_transfer) }
    val details = when (transaction) {
        is Transaction.Income -> "${categoryLabel(categories[transaction.categoryId])} · ${accountLabel(accounts[transaction.accountId])}"
        is Transaction.Expense -> "${categoryLabel(categories[transaction.categoryId])} · ${accountLabel(accounts[transaction.accountId])}"
        is Transaction.Transfer -> stringResource(
            R.string.transfer_from_to,
            accountLabel(accounts[transaction.fromAccountId]),
            accountLabel(accounts[transaction.toAccountId]),
        )
    }
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        leadingContent = {
            Surface(shape = CircleShape, color = container, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(iconRes), contentDescription = stringResource(typeLabel), tint = onContainer)
                }
            }
        },
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                if (showDate) "$details\n${formatDate(transaction.date)}" else details,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            when (transaction) {
                is Transaction.Income -> MoneyText(transaction.amount, color = colors.tertiary, sign = AmountSign.PLUS)
                is Transaction.Expense -> MoneyText(transaction.amount, color = colors.error, sign = AmountSign.MINUS)
                is Transaction.Transfer -> MoneyText(transaction.amount, color = colors.onSurfaceVariant)
            }
        },
    )
}

private data class Quad(val icon: Int, val label: Int, val container: Color, val onContainer: Color)

/** Read-only field that opens the Material date picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    date: LocalDate?,
    onDateChange: (LocalDate) -> Unit,
    @StringRes labelRes: Int,
    modifier: Modifier = Modifier,
    @StringRes errorRes: Int? = null,
) {
    var open by remember { mutableStateOf(false) }
    val chooseLabel = stringResource(R.string.action_choose_date)
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = date?.let { formatDate(it) } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(labelRes)) },
            trailingIcon = { Icon(painterResource(R.drawable.ic_event), contentDescription = null) },
            isError = errorRes != null,
            supportingText = errorRes?.let { { Text(stringResource(it)) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable(onClickLabel = chooseLabel, role = Role.Button) { open = true },
        )
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date?.let(PickerDates::toMillis))
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDateChange(PickerDates.fromMillis(it)) }
                    open = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = state) }
    }
}

/** Tappable choices instead of typing (faster, fewer errors). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
            )
        }
    }
}

@Composable
fun FieldError(@StringRes errorRes: Int?) {
    if (errorRes != null) {
        Text(
            stringResource(errorRes),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = Spacing.md),
        )
    }
}
