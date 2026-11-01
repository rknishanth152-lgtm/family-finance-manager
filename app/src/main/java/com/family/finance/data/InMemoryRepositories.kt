package com.family.finance.data

import com.family.finance.domain.model.Account
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.Category
import com.family.finance.domain.model.CategoryId
import com.family.finance.domain.model.NoteEntry
import com.family.finance.domain.model.NoteId
import com.family.finance.domain.model.Transaction
import com.family.finance.domain.model.TransactionId
import com.family.finance.domain.repository.AccountRepository
import com.family.finance.domain.repository.CategoryRepository
import com.family.finance.domain.repository.NoteRepository
import com.family.finance.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Phase 2 storage: process memory only, lost on restart. Phase 4 replaces these with
 * Firestore-backed implementations of the same interfaces.
 */
internal class InMemoryStore<K : Any, V : Any>(private val keyOf: (V) -> K, initial: List<V> = emptyList()) {
    private val state = MutableStateFlow(initial.associateBy(keyOf))

    fun observe(): Flow<List<V>> = state.map { it.values.toList() }
    fun get(key: K): V? = state.value[key]
    fun upsert(value: V) = state.update { it + (keyOf(value) to value) }
    fun delete(key: K) = state.update { it - key }
}

class InMemoryTransactionRepository : TransactionRepository {
    private val store = InMemoryStore<TransactionId, Transaction>({ it.id })
    override fun observeAll() = store.observe()
    override suspend fun get(id: TransactionId) = store.get(id)
    override suspend fun upsert(transaction: Transaction) = store.upsert(transaction)
    override suspend fun delete(id: TransactionId) = store.delete(id)
}

class InMemoryAccountRepository(initial: List<Account> = emptyList()) : AccountRepository {
    private val store = InMemoryStore<AccountId, Account>({ it.id }, initial)
    override fun observeAll() = store.observe()
    override suspend fun get(id: AccountId) = store.get(id)
    override suspend fun upsert(account: Account) = store.upsert(account)
}

class InMemoryCategoryRepository(initial: List<Category> = emptyList()) : CategoryRepository {
    private val store = InMemoryStore<CategoryId, Category>({ it.id }, initial)
    override fun observeAll() = store.observe()
    override suspend fun get(id: CategoryId) = store.get(id)
    override suspend fun upsert(category: Category) = store.upsert(category)
}

class InMemoryNoteRepository : NoteRepository {
    private val store = InMemoryStore<NoteId, NoteEntry>({ it.id })
    override fun observeAll() = store.observe()
    override suspend fun get(id: NoteId) = store.get(id)
    override suspend fun upsert(note: NoteEntry) = store.upsert(note)
    override suspend fun delete(id: NoteId) = store.delete(id)
}
