package com.family.finance.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.finance.domain.model.Account
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.Category
import com.family.finance.domain.model.CategoryId
import com.family.finance.domain.model.Transaction
import com.family.finance.domain.model.TransactionId
import com.family.finance.domain.repository.AccountRepository
import com.family.finance.domain.repository.CategoryRepository
import com.family.finance.domain.repository.TransactionRepository
import com.family.finance.domain.usecase.TransactionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetailUiState(
    val loading: Boolean = true,
    val transaction: Transaction? = null,
    val accounts: Map<AccountId, Account> = emptyMap(),
    val categories: Map<CategoryId, Category> = emptyMap(),
    val deleted: Boolean = false,
)

class TransactionDetailViewModel(
    private val id: TransactionId,
    transactions: TransactionRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    private val service: TransactionService,
) : ViewModel() {

    private val deleted = MutableStateFlow(false)

    val uiState: StateFlow<DetailUiState> = combine(
        transactions.observeAll(), accounts.observeAll(), categories.observeAll(), deleted,
    ) { txns, accts, cats, del ->
        DetailUiState(
            loading = false,
            transaction = txns.firstOrNull { it.id == id },
            accounts = accts.associateBy { it.id },
            categories = cats.associateBy { it.id },
            deleted = del,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    /** Called only after the user confirms (PRD 6.7). */
    fun delete() {
        viewModelScope.launch {
            service.delete(id)
            deleted.value = true
        }
    }
}
