package com.family.finance.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.finance.domain.calc.BalanceCalculator
import com.family.finance.domain.model.Account
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.AccountType
import com.family.finance.domain.model.Money
import com.family.finance.domain.model.MoneyFormatter
import com.family.finance.domain.repository.AccountRepository
import com.family.finance.domain.repository.TransactionRepository
import com.family.finance.domain.usecase.AccountService
import com.family.finance.domain.usecase.RecordResult
import com.family.finance.domain.validation.AccountDraft
import com.family.finance.domain.validation.ValidationError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class AccountRow(val account: Account, val balance: Money)

data class AccountsUiState(
    val loading: Boolean = true,
    val active: List<AccountRow> = emptyList(),
    val archived: List<AccountRow> = emptyList(),
    val combined: Money = Money.ZERO,
)

class AccountsViewModel(
    accounts: AccountRepository,
    transactions: TransactionRepository,
    private val service: AccountService,
    private val today: () -> LocalDate,
) : ViewModel() {

    val uiState: StateFlow<AccountsUiState> = combine(accounts.observeAll(), transactions.observeAll()) { accts, txns ->
        val rows = accts.sortedBy { it.name.lowercase() }
            .map { AccountRow(it, BalanceCalculator.accountBalance(it, txns, asOf = today())) }
        AccountsUiState(
            loading = false,
            active = rows.filter { it.account.isActive },
            archived = rows.filterNot { it.account.isActive },
            // Archived accounts still hold their money, so they stay in the family total.
            combined = BalanceCalculator.combinedBalance(accts, txns, asOf = today()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    fun setActive(id: AccountId, active: Boolean) {
        viewModelScope.launch { service.setActive(id, active) }
    }
}

data class AccountEditState(
    val isEdit: Boolean,
    val loaded: Boolean = false,
    val name: String = "",
    val type: AccountType = AccountType.CASH,
    val openingBalanceText: String = "",
    val openingBalanceDate: LocalDate? = null,
    val errors: List<ValidationError> = emptyList(),
    val saving: Boolean = false,
    val saved: Boolean = false,
)

class AccountEditViewModel(
    editId: AccountId?,
    private val accounts: AccountRepository,
    private val service: AccountService,
    today: () -> LocalDate,
) : ViewModel() {

    private val id = editId ?: AccountId.generate()
    private val _state = MutableStateFlow(AccountEditState(isEdit = editId != null))
    val state: StateFlow<AccountEditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = editId?.let { accounts.get(it) }
            _state.update {
                if (existing != null) {
                    it.copy(
                        loaded = true,
                        name = existing.name,
                        type = existing.type,
                        openingBalanceText = MoneyFormatter.plain(existing.openingBalance),
                        openingBalanceDate = existing.openingBalanceDate,
                    )
                } else {
                    it.copy(loaded = true, openingBalanceDate = today())
                }
            }
        }
    }

    fun edit(transform: (AccountEditState) -> AccountEditState) = _state.update { transform(it).copy(errors = emptyList()) }

    fun save() {
        val s = _state.value
        if (s.saving || s.saved || !s.loaded) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val r = service.save(id, AccountDraft(s.name, s.type, s.openingBalanceText, s.openingBalanceDate))) {
                is RecordResult.Saved -> _state.update { it.copy(saving = false, saved = true) }
                is RecordResult.Invalid -> _state.update { it.copy(saving = false, errors = r.errors) }
            }
        }
    }
}
