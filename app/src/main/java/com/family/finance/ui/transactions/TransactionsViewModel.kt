package com.family.finance.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.finance.domain.calc.DateRange
import com.family.finance.domain.model.Account
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.Category
import com.family.finance.domain.model.CategoryId
import com.family.finance.domain.model.Money
import com.family.finance.domain.model.Transaction
import com.family.finance.domain.repository.AccountRepository
import com.family.finance.domain.repository.CategoryRepository
import com.family.finance.domain.repository.TransactionRepository
import com.family.finance.domain.usecase.SortOrder
import com.family.finance.domain.usecase.TransactionQuery
import com.family.finance.domain.usecase.TypeFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/** Raw filter input as typed; amounts are parsed leniently (invalid text is ignored). */
data class FilterInput(
    val type: TypeFilter = TypeFilter.ALL,
    val text: String = "",
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val accountId: AccountId? = null,
    val categoryId: CategoryId? = null,
    val minAmountText: String = "",
    val maxAmountText: String = "",
    val sort: SortOrder = SortOrder.DATE_DESC,
) {
    val activeFilterCount: Int
        get() = listOf(
            from != null || to != null,
            accountId != null,
            categoryId != null,
            minAmountText.isNotBlank() || maxAmountText.isNotBlank(),
        ).count { it }

    fun toQuery(): TransactionQuery {
        val range = when {
            from != null && to != null -> if (to.isBefore(from)) DateRange(to, from) else DateRange(from, to)
            from != null -> DateRange(from, LocalDate.MAX)
            to != null -> DateRange(LocalDate.MIN, to)
            else -> null
        }
        return TransactionQuery(
            type = type,
            text = text,
            range = range,
            accountId = accountId,
            categoryId = categoryId,
            minAmount = Money.parse(minAmountText),
            maxAmount = Money.parse(maxAmountText),
            sort = sort,
        )
    }
}

data class TransactionsUiState(
    val loading: Boolean = true,
    val filter: FilterInput = FilterInput(),
    val items: List<Transaction> = emptyList(),
    val accounts: Map<AccountId, Account> = emptyMap(),
    val categories: Map<CategoryId, Category> = emptyMap(),
)

class TransactionsViewModel(
    transactions: TransactionRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(FilterInput())

    /** Category display names supplied by the screen, so search matches what the user sees. */
    private val categoryNames = MutableStateFlow<Map<CategoryId, String>>(emptyMap())

    val uiState: StateFlow<TransactionsUiState> = combine(
        filter, transactions.observeAll(), accounts.observeAll(), categories.observeAll(), categoryNames,
    ) { f, txns, accts, cats, names ->
        TransactionsUiState(
            loading = false,
            filter = f,
            items = f.toQuery().apply(txns) { names[it].orEmpty() },
            accounts = accts.associateBy { it.id },
            categories = cats.associateBy { it.id },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    fun updateFilter(transform: (FilterInput) -> FilterInput) = filter.update(transform)
    fun clearFilters() = filter.update { FilterInput(type = it.type, text = it.text, sort = it.sort) }
    fun setCategoryNames(names: Map<CategoryId, String>) {
        categoryNames.value = names
    }
}
