package com.family.finance.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.finance.domain.calc.DailyTotals
import com.family.finance.domain.calc.DayTotal
import com.family.finance.domain.model.Account
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.Category
import com.family.finance.domain.model.CategoryId
import com.family.finance.domain.model.Transaction
import com.family.finance.domain.repository.AccountRepository
import com.family.finance.domain.repository.CategoryRepository
import com.family.finance.domain.repository.TransactionRepository
import com.family.finance.domain.usecase.TransactionQuery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

data class CalendarUiState(
    val month: YearMonth,
    val selected: LocalDate,
    val today: LocalDate,
    val totals: Map<LocalDate, DayTotal> = emptyMap(),
    val dayTransactions: List<Transaction> = emptyList(),
    val accounts: Map<AccountId, Account> = emptyMap(),
    val categories: Map<CategoryId, Category> = emptyMap(),
)

class CalendarViewModel(
    transactions: TransactionRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    private val today: () -> LocalDate,
) : ViewModel() {

    private data class Selection(val month: YearMonth, val day: LocalDate)

    private val selection = MutableStateFlow(today().let { Selection(YearMonth.from(it), it) })

    val uiState: StateFlow<CalendarUiState> = combine(
        selection, transactions.observeAll(), accounts.observeAll(), categories.observeAll(),
    ) { sel, txns, accts, cats ->
        CalendarUiState(
            month = sel.month,
            selected = sel.day,
            today = today(),
            totals = DailyTotals.forMonth(txns, sel.month),
            dayTransactions = TransactionQuery().apply(txns.filter { it.date == sel.day }),
            accounts = accts.associateBy { it.id },
            categories = cats.associateBy { it.id },
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CalendarUiState(selection.value.month, selection.value.day, today()),
    )

    fun previousMonth() = showMonth(selection.value.month.minusMonths(1))
    fun nextMonth() = showMonth(selection.value.month.plusMonths(1))

    fun select(date: LocalDate) {
        selection.value = Selection(YearMonth.from(date), date)
    }

    /** Moving months selects the 1st, or today when returning to the current month. */
    private fun showMonth(month: YearMonth) {
        val now = today()
        selection.value = Selection(month, if (YearMonth.from(now) == month) now else month.atDay(1))
    }
}
