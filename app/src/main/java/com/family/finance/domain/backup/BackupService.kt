package com.family.finance.domain.backup

import com.family.finance.domain.model.Account
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.Category
import com.family.finance.domain.model.CategoryId
import com.family.finance.domain.model.DefaultCategories
import com.family.finance.domain.model.NoteEntry
import com.family.finance.domain.model.Transaction
import com.family.finance.domain.model.UserId
import com.family.finance.domain.repository.AccountRepository
import com.family.finance.domain.repository.CategoryRepository
import com.family.finance.domain.repository.NoteRepository
import com.family.finance.domain.repository.TransactionRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant

/**
 * What a restore would do, worked out before anything is written (PRD 12.7). Restore only
 * ever adds records that are missing; a record that is already here is never changed,
 * even if the backup's copy differs (those are counted in [differ]).
 */
data class RestorePlan(
    val backup: Backup,
    /** Exactly the records that would be added. */
    val toAdd: Backup,
    val alreadyHere: Int,
    val differ: Int,
    /** Records deleted here since the backup was made; they stay deleted. */
    val deletedHere: Int,
    /** Entries pointing at an account or category found neither in the backup nor here. */
    val brokenLinks: List<String>,
) {
    val canRestore: Boolean get() = brokenLinks.isEmpty()
    val addCount: Int get() = toAdd.accounts.size + toAdd.categories.size + toAdd.transactions.size + toAdd.notes.size
}

data class RestoreResult(val accounts: Int, val categories: Int, val transactions: Int, val notes: Int) {
    val total: Int get() = accounts + categories + transactions + notes
}

/**
 * Full backup and "add what is missing" restore (PRD 12.5, 12.7). Records are written
 * straight to storage, not through the entry forms, so archived accounts and categories
 * come back exactly as they were.
 *
 * Restored records are created by the person restoring: the cloud only lets someone
 * create records in their own name. Dates, amounts and text are kept exactly.
 */
class BackupService(
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val notes: NoteRepository,
    private val currentUser: () -> UserId,
    private val clock: Clock,
) {

    suspend fun create(familyName: String): Backup = Backup(
        createdAt = Instant.now(clock),
        familyName = familyName,
        accounts = accounts.observeAll().first().sortedBy { it.id.value },
        categories = categories.observeAll().first().sortedBy { it.id.value },
        transactions = transactions.observeAll().first().sortedWith(compareBy({ it.date }, { it.id.value })),
        notes = notes.observeAll().first().sortedBy { it.id.value },
    )

    suspend fun plan(backup: Backup): RestorePlan {
        val currentAccounts = accounts.observeAll().first().associateBy { it.id }
        val currentCategories = categories.observeAll().first().associateBy { it.id }
        val currentTransactions = transactions.observeAll().first().associateBy { it.id }
        val currentNotes = notes.observeAll().first().associateBy { it.id }
        val deletedTransactions = transactions.observeDeletedIds().first()
        val deletedNotes = notes.observeDeletedIds().first()

        var here = 0
        var differ = 0
        fun <K, T> split(items: List<T>, key: (T) -> K, current: Map<K, T>, same: (T, T) -> Boolean): List<T> =
            items.filter { item ->
                val existing = current[key(item)]
                when {
                    existing == null -> true
                    same(existing, item) -> { here++; false }
                    else -> { here++; differ++; false }
                }
            }

        val addAccounts = split(backup.accounts, { it.id }, currentAccounts) { a, b -> a.normalized() == b.normalized() }
        val addCategories = split(backup.categories, { it.id }, currentCategories) { a, b -> a == b }
        // A record deleted here after the backup was made is not missing: restoring it would
        // undo someone's deletion, so it is counted apart and left deleted.
        val deletedHere = backup.transactions.count { it.id in deletedTransactions } + backup.notes.count { it.id in deletedNotes }
        val addTransactions = split(backup.transactions.filter { it.id !in deletedTransactions }, { it.id }, currentTransactions) { a, b -> a.normalized() == b.normalized() }
        val addNotes = split(backup.notes.filter { it.id !in deletedNotes }, { it.id }, currentNotes) { a, b -> a.normalized() == b.normalized() }

        val knownAccounts: Set<AccountId> = currentAccounts.keys + backup.accounts.map { it.id }
        val knownCategories: Set<CategoryId> = currentCategories.keys + backup.categories.map { it.id } + DefaultCategories.all.map { it.id }
        val broken = addTransactions.flatMap { t ->
            val accountIds = when (t) {
                is Transaction.Income -> listOf(t.accountId)
                is Transaction.Expense -> listOf(t.accountId)
                is Transaction.Transfer -> listOf(t.fromAccountId, t.toAccountId)
            }
            val categoryId = when (t) {
                is Transaction.Income -> t.categoryId
                is Transaction.Expense -> t.categoryId
                is Transaction.Transfer -> null
            }
            accountIds.filter { it !in knownAccounts }.map { "${t.id.value} → account ${it.value}" } +
                listOfNotNull(categoryId?.takeIf { it !in knownCategories }?.let { "${t.id.value} → category ${it.value}" })
        }

        return RestorePlan(
            backup = backup,
            toAdd = backup.copy(accounts = addAccounts, categories = addCategories, transactions = addTransactions, notes = addNotes),
            alreadyHere = here,
            differ = differ,
            deletedHere = deletedHere,
            brokenLinks = broken,
        )
    }

    /**
     * Adds the planned records. Each one is looked up again right before writing, so a
     * record that appeared since planning (e.g. synced from the other phone) is left alone.
     * Accounts and categories go first, so entries never point at something missing.
     */
    suspend fun restore(plan: RestorePlan): RestoreResult {
        require(plan.canRestore) { "Backup has broken links" }
        val me = currentUser()
        val add = plan.toAdd
        var a = 0; var c = 0
        val t: Int
        val n: Int
        // Built-in categories always exist, so only custom ones can be missing.
        for (item in add.categories) if (categories.get(item.id) == null) { categories.upsert(item); c++ }
        for (item in add.accounts) if (accounts.get(item.id) == null) { accounts.upsert(item); a++ }
        // Each lookup can be a round trip to the cloud, so many run at once; every record is
        // still checked individually right before it is written.
        val deletedTransactions = transactions.observeDeletedIds().first()
        val deletedNotes = notes.observeDeletedIds().first()
        t = addMissing(add.transactions.filter { it.id !in deletedTransactions }) { item -> if (transactions.get(item.id) == null) { transactions.upsert(item.authoredBy(me)); true } else false }
        n = addMissing(add.notes.filter { it.id !in deletedNotes }) { item -> if (notes.get(item.id) == null) { notes.upsert(item.copy(createdBy = me)); true } else false }
        return RestoreResult(a, c, t, n)
    }

    private suspend fun <T> addMissing(items: List<T>, add: suspend (T) -> Boolean): Int = coroutineScope {
        items.chunked(PARALLEL_WRITES).sumOf { chunk -> chunk.map { async { add(it) } }.awaitAll().count { it } }
    }

    private fun Account.normalized() = copy(createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
    private fun NoteEntry.normalized() = copy(createdBy = NOBODY, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
    private fun Transaction.normalized(): Transaction = authoredBy(NOBODY).let {
        when (it) {
            is Transaction.Income -> it.copy(createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
            is Transaction.Expense -> it.copy(createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
            is Transaction.Transfer -> it.copy(createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        }
    }

    private companion object {
        val NOBODY = UserId("")
        const val PARALLEL_WRITES = 16
    }
}

/** The same record, created in [user]'s name. */
fun Transaction.authoredBy(user: UserId): Transaction = when (this) {
    is Transaction.Income -> copy(createdBy = user)
    is Transaction.Expense -> copy(createdBy = user)
    is Transaction.Transfer -> copy(createdBy = user)
}
