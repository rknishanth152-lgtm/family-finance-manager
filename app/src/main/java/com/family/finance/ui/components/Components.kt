package com.family.finance.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Card
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.sp
import com.family.finance.domain.calc.IncomeExpenseTotals
import com.family.finance.domain.sync.SyncState
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
    // An amount must never lose digits: shrink (down to 55%) rather than cut, never wrap.
    val size = style.fontSize
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color.takeOrElse { LocalContentColor.current }),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = size * 0.55f, maxFontSize = size, stepSize = 0.5.sp),
    )
}

/** At this phone font scale and above, side-by-side layouts switch to stacked rows. */
const val LARGE_FONT_SCALE = 1.3f

@Composable
@ReadOnlyComposable
fun isLargeFont(): Boolean = LocalDensity.current.fontScale >= LARGE_FONT_SCALE

/**
 * Income | Expense | Balance, like the app the family used before. Balance = income - expense
 * and is recalculated from the transactions every time, so it always adds up.
 * With large phone fonts the three columns become three rows so no amount is cut.
 */
@Composable
fun IncomeExpenseStrip(totals: IncomeExpenseTotals, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Card(modifier = modifier.fillMaxWidth()) {
        if (isLargeFont()) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StripRow(R.string.summary_income, totals.income, colors.tertiary)
                StripRow(R.string.summary_expense, totals.expense, colors.error)
                StripRow(R.string.summary_balance, totals.balance, colors.onSurface)
            }
        } else {
            Row(Modifier.padding(vertical = Spacing.md, horizontal = Spacing.sm)) {
                StripCell(R.string.summary_income, totals.income, colors.tertiary, Modifier.weight(1f))
                StripCell(R.string.summary_expense, totals.expense, colors.error, Modifier.weight(1f))
                StripCell(R.string.summary_balance, totals.balance, colors.onSurface, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StripRow(@StringRes labelRes: Int, amount: Money, color: Color) {
    val label = stringResource(labelRes)
    val spoken = "$label ${MoneyFormatter.format(amount)}"
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = spoken }) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText(amount, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
private fun StripCell(@StringRes labelRes: Int, amount: Money, color: Color, modifier: Modifier) {
    val label = stringResource(labelRes)
    val spoken = "$label ${MoneyFormatter.format(amount)}"
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        // Large amounts (e.g. ₹3,48,26,190.00) shrink to fit a third of the width instead of being cut.
        BasicText(
            text = MoneyFormatter.format(amount),
            style = MaterialTheme.typography.titleMedium.copy(color = color, textAlign = TextAlign.Center),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 18.sp, stepSize = 0.5.sp),
        )
    }
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

/** "Synced / Syncing / Waiting / Offline / Problem" (PRD 6.1 item 14, 11.2). Icon + text, never colour alone. */
@Composable
fun SyncIndicator(state: SyncState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (icon, label, tint) = when (state) {
        SyncState.SYNCED -> Triple(R.drawable.ic_cloud_done, R.string.sync_synced, colors.onSurfaceVariant)
        SyncState.SYNCING -> Triple(R.drawable.ic_cloud_sync, R.string.sync_syncing, colors.primary)
        SyncState.PENDING -> Triple(R.drawable.ic_cloud_sync, R.string.sync_pending, colors.primary)
        SyncState.OFFLINE -> Triple(R.drawable.ic_cloud_off, R.string.sync_offline, colors.onSurfaceVariant)
        SyncState.ERROR -> Triple(R.drawable.ic_sync_problem, R.string.sync_error, colors.error)
    }
    Row(
        modifier = modifier.padding(horizontal = Spacing.sm).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1)
    }
}

/** Warning card used for "changed on both phones" (PRD 9.4, 11.5). */
@Composable
fun WarningCard(title: String, message: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Icon(painterResource(R.drawable.ic_warning), contentDescription = null)
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            Text(message, style = MaterialTheme.typography.bodyMedium)
            action()
        }
    }
}
