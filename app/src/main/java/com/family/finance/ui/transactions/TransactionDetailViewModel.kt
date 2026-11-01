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
import com.family.finance.domain.model.Member
import com.family.finance.domain.model.UserId
import com.family.finance.domain.sync.ConflictDetector
import com.family.finance.domain.sync.EditEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
    /** Versions saved on both phones without seeing each other (empty when there is no conflict). */
    val competing: List<EditEntry> = emptyList(),
    val names: Map<UserId, String> = emptyMap(),
)

class TransactionDetailViewModel(
    private val id: TransactionId,
    transactions: TransactionRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    private val service: TransactionService,
    members: Flow<List<Member>> = flowOf(emptyList()),
) : ViewModel() {

    private val deleted = MutableStateFlow(false)

    private val conflict = combine(transactions.observeEditLog(), members) { log, people ->
        ConflictDetector.competingEdits(log[id].orEmpty()) to people.associate { it.uid to it.displayName.ifBlank { it.email } }
    }

    val uiState: StateFlow<DetailUiState> = combine(
        transactions.observeAll(), accounts.observeAll(), categories.observeAll(), deleted, conflict,
    ) { txns, accts, cats, del, (competing, names) ->
        DetailUiState(
            loading = false,
            transaction = txns.firstOrNull { it.id == id },
            accounts = accts.associateBy { it.id },
            categories = cats.associateBy { it.id },
            deleted = del,
            competing = competing,
            names = names,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    fun confirmCurrent() { viewModelScope.launch { service.confirm(id) } }

    fun restore(version: Transaction) { viewModelScope.launch { service.restore(version) } }

    /** Called only after the user confirms (PRD 6.7). */
    fun delete() {
        viewModelScope.launch {
            service.delete(id)
            deleted.value = true
        }
    }
}
