package com.family.finance.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.finance.domain.calc.BalanceCalculator
import com.family.finance.domain.calc.PeriodSummary
import com.family.finance.domain.calc.ReportingPeriod
import com.family.finance.domain.model.Account
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.Category
import com.family.finance.domain.model.CategoryId
import com.family.finance.domain.model.Money
import com.family.finance.domain.model.Transaction
import com.family.finance.domain.repository.AccountRepository
import com.family.finance.domain.repository.CategoryRepository
import com.family.finance.domain.repository.TransactionRepository
import com.family.finance.domain.usecase.TransactionQuery
import com.family.finance.domain.model.TransactionId
import com.family.finance.domain.sync.ConflictDetector
import com.family.finance.domain.sync.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate

enum class PeriodKind { TODAY, THIS_WEEK, THIS_MONTH, LAST_MONTH, THIS_YEAR }

data class HomeUiState(
    val loading: Boolean = true,
    val period: ReportingPeriod,
    val summary: PeriodSummary? = null,
    /** Combined balance of all accounts as of today (PRD 6.1 "overall available balance"). */
    val currentBalance: Money = Money.ZERO,
    val recent: List<Transaction> = emptyList(),
    val accounts: Map<AccountId, Account> = emptyMap(),
    val categories: Map<CategoryId, Category> = emptyMap(),
    val sync: SyncState = SyncState.SYNCING,
    /** Entries edited on both phones without seeing each other's change (PRD 9.4). */
    val conflicted: List<TransactionId> = emptyList(),
) {
    val hasAccounts: Boolean get() = accounts.isNotEmpty()
}

class HomeViewModel(
    transactions: TransactionRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    private val today: () -> LocalDate,
    syncState: Flow<SyncState> = flowOf(SyncState.SYNCED),
) : ViewModel() {

    private val period = MutableStateFlow(ReportingPeriod.thisMonth(today()))

    private val status = combine(syncState, transactions.observeEditLog()) { sync, log ->
        sync to log.filterValues { ConflictDetector.hasConflict(it) }.keys.toList()
    }

    val uiState: StateFlow<HomeUiState> = combine(
        period, transactions.observeAll(), accounts.observeAll(), categories.observeAll(), status,
    ) { period, txns, accts, cats, (sync, conflicted) ->
        HomeUiState(
            loading = false,
            period = period,
            summary = PeriodSummary.calculate(accts, txns, period.range),
            currentBalance = BalanceCalculator.combinedBalance(accts, txns, asOf = today()),
            recent = TransactionQuery().apply(txns).take(RECENT_COUNT),
            accounts = accts.associateBy { it.id },
            categories = cats.associateBy { it.id },
            sync = sync,
            conflicted = conflicted,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(period = period.value))

    fun previousPeriod() = period.update { it.previous() }
    fun nextPeriod() = period.update { it.next() }

    fun selectPeriod(kind: PeriodKind) {
        val d = today()
        period.value = when (kind) {
            PeriodKind.TODAY -> ReportingPeriod.today(d)
            PeriodKind.THIS_WEEK -> ReportingPeriod.thisWeek(d)
            PeriodKind.THIS_MONTH -> ReportingPeriod.thisMonth(d)
            PeriodKind.LAST_MONTH -> ReportingPeriod.lastMonth(d)
            PeriodKind.THIS_YEAR -> ReportingPeriod.thisYear(d)
        }
    }

    companion object {
        const val RECENT_COUNT = 5
    }
}
