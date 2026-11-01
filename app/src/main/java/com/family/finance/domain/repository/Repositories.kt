package com.family.finance.domain.repository

import com.family.finance.domain.model.Account
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.Category
import com.family.finance.domain.model.CategoryId
import com.family.finance.domain.model.Invite
import com.family.finance.domain.model.Member
import com.family.finance.domain.model.NoteEntry
import com.family.finance.domain.model.NoteId
import com.family.finance.domain.model.Transaction
import com.family.finance.domain.model.TransactionId
import com.family.finance.domain.model.UserId
import com.family.finance.domain.model.Workspace
import com.family.finance.domain.sync.EditEntry
import kotlinx.coroutines.flow.Flow

/*
 * Storage contracts. Writes are upserts keyed by stable ID, so repeating a write
 * (double tap, retry) never creates a second record (PRD 9.3).
 * Firestore implementations sync between the two phones; in-memory ones back unit tests.
 * Writes must not wait for the network: they land in the phone's copy immediately and
 * sync later (PRD 4.5).
 */

interface TransactionRepository {
    fun observeAll(): Flow<List<Transaction>>
    suspend fun get(id: TransactionId): Transaction?
    suspend fun upsert(transaction: Transaction)

    /** Soft delete: the record disappears from lists but the deletion syncs and is logged. */
    suspend fun delete(id: TransactionId)

    /** Every save (and delete) of each transaction, used to spot edits made on both phones. */
    fun observeEditLog(): Flow<Map<TransactionId, List<EditEntry>>>
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

/** The family workspace as seen by the signed-in person (PRD 3.3, 10.2). */
interface FamilyRepository {
    val workspace: Workspace
    val me: Member

    fun observeMembers(): Flow<List<Member>>
    fun observeInvites(): Flow<List<Invite>>

    /** Admin only. The invited person joins by signing in with this Google account. */
    suspend fun invite(email: String)
    suspend fun cancelInvite(email: String)

    /** Admin only; never the admin themselves. Their records stay (PRD 9.5). */
    suspend fun removeMember(uid: UserId)
}
