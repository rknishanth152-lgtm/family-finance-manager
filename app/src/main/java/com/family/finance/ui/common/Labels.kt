package com.family.finance.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.family.finance.R
import com.family.finance.domain.calc.ReportingPeriod
import com.family.finance.domain.model.Account
import com.family.finance.domain.model.AccountType
import com.family.finance.domain.model.Category
import com.family.finance.domain.validation.Field
import com.family.finance.domain.validation.Reason
import com.family.finance.domain.validation.ValidationError
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@StringRes
fun categorySystemLabel(key: String): Int? = when (key) {
    "agricultural_income" -> R.string.cat_agricultural_income
    "business_income" -> R.string.cat_business_income
    "salary" -> R.string.cat_salary
    "interest_received" -> R.string.cat_interest_received
    "investment_income" -> R.string.cat_investment_income
    "family_received" -> R.string.cat_family_received
    "loan_received" -> R.string.cat_loan_received
    "refund" -> R.string.cat_refund
    "other_income" -> R.string.cat_other_income
    "groceries" -> R.string.cat_groceries
    "food" -> R.string.cat_food
    "household_supplies" -> R.string.cat_household_supplies
    "electricity" -> R.string.cat_electricity
    "water" -> R.string.cat_water
    "internet" -> R.string.cat_internet
    "mobile_recharge" -> R.string.cat_mobile_recharge
    "rent" -> R.string.cat_rent
    "seeds" -> R.string.cat_seeds
    "fertilizers" -> R.string.cat_fertilizers
    "pesticides" -> R.string.cat_pesticides
    "agri_labour" -> R.string.cat_agri_labour
    "machinery" -> R.string.cat_machinery
    "equipment" -> R.string.cat_equipment
    "irrigation" -> R.string.cat_irrigation
    "transport" -> R.string.cat_transport
    "repairs" -> R.string.cat_repairs
    "other_agri" -> R.string.cat_other_agri
    "medical" -> R.string.cat_medical
    "education" -> R.string.cat_education
    "travel" -> R.string.cat_travel
    "vehicle_maintenance" -> R.string.cat_vehicle_maintenance
    "insurance" -> R.string.cat_insurance
    "entertainment" -> R.string.cat_entertainment
    "gifts" -> R.string.cat_gifts
    "other_expenses" -> R.string.cat_other_expenses
    "loan_principal" -> R.string.cat_loan_principal
    "loan_interest" -> R.string.cat_loan_interest
    "bank_charges" -> R.string.cat_bank_charges
    "taxes" -> R.string.cat_taxes
    else -> null
}

/** Custom name wins; otherwise a built-in category shows in the viewer's language. */
@Composable
@ReadOnlyComposable
fun categoryLabel(category: Category?): String {
    if (category == null) return stringResource(R.string.label_unknown)
    if (category.name.isNotBlank()) return category.name
    val res = category.systemKey?.let(::categorySystemLabel) ?: return stringResource(R.string.label_unknown)
    return stringResource(res)
}

@Composable
@ReadOnlyComposable
fun accountLabel(account: Account?): String = account?.name ?: stringResource(R.string.label_unknown)

@StringRes
fun accountTypeLabel(type: AccountType): Int = when (type) {
    AccountType.CASH -> R.string.account_type_cash
    AccountType.SAVINGS -> R.string.account_type_savings
    AccountType.CURRENT -> R.string.account_type_current
    AccountType.DIGITAL_WALLET -> R.string.account_type_wallet
    AccountType.OTHER -> R.string.account_type_other
}

@StringRes
fun ValidationError.messageRes(): Int = when (reason) {
    Reason.REQUIRED -> when (field) {
        Field.CATEGORY -> R.string.error_choose_category
        Field.ACCOUNT, Field.FROM_ACCOUNT, Field.TO_ACCOUNT -> R.string.error_choose_account
        Field.DATE -> R.string.error_choose_date
        else -> R.string.error_required
    }
    Reason.INVALID_FORMAT -> R.string.error_amount_format
    Reason.NOT_POSITIVE -> R.string.error_amount_positive
    Reason.NOT_FOUND -> R.string.error_not_found
    Reason.INACTIVE -> R.string.error_account_archived
    Reason.ARCHIVED -> R.string.error_category_archived
    Reason.WRONG_KIND -> R.string.error_category_wrong_kind
    Reason.SAME_AS_SOURCE -> R.string.error_same_account
    Reason.TOO_LARGE -> R.string.error_amount_too_large
}

@StringRes
fun List<ValidationError>.messageFor(field: Field): Int? = firstOrNull { it.field == field }?.messageRes()

@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

@Composable
@ReadOnlyComposable
fun formatDate(date: LocalDate): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(currentLocale()).format(date)

@Composable
@ReadOnlyComposable
fun formatMonth(month: YearMonth): String =
    DateTimeFormatter.ofPattern("LLLL yyyy", currentLocale()).format(month)

@Composable
@ReadOnlyComposable
fun periodLabel(period: ReportingPeriod): String = when (period) {
    is ReportingPeriod.Day -> formatDate(period.date)
    is ReportingPeriod.Month -> formatMonth(period.month)
    is ReportingPeriod.Year -> period.year.toString()
    is ReportingPeriod.Week, is ReportingPeriod.Custom ->
        stringResource(R.string.period_range, formatDate(period.range.start), formatDate(period.range.end))
}
