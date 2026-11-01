package com.family.finance.domain.repository

import com.family.finance.domain.model.Account
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.Category
import com.family.finance.domain.model.CategoryId
import com.family.finance.domain.model.NoteEntry
import com.family.finance.domain.model.NoteId
import com.family.finance.domain.model.Transaction
import com.family.finance.domain.model.TransactionId
import kotlinx.coroutines.flow.Flow

/*
 * Storage contracts. Writes are upserts keyed by stable ID, so repeating a write
 * (double tap, retry) never creates a second record (PRD 9.3).
 * Phase 2 uses in-memory implementations; Phase 4 swaps in Firestore.
 */

interface TransactionRepository {
    fun observeAll(): Flow<List<Transaction>>
    suspend fun get(id: TransactionId): Transaction?
    suspend fun upsert(transaction: Transaction)
    suspend fun delete(id: TransactionId)
}

interface AccountRepository {
    fun observeAll(): Flow<List<Account>>
    suspend fun get(id: AccountId): Account?
    suspend fun upsert(account: Account)
}

interface CategoryRepository {
    fun observeAll(): Flow<List<Category>>
    suspend fun get(id: CategoryId): Category?
    suspend fun upsert(category: Category)
}

interface NoteRepository {
    fun observeAll(): Flow<List<NoteEntry>>
    suspend fun get(id: NoteId): NoteEntry?
    suspend fun upsert(note: NoteEntry)
    suspend fun delete(id: NoteId)
}
