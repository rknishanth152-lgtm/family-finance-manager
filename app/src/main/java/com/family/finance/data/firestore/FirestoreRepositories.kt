package com.family.finance.data.firestore

import com.family.finance.domain.model.Account
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.Category
import com.family.finance.domain.model.CategoryId
import com.family.finance.domain.model.DefaultCategories
import com.family.finance.domain.model.NoteEntry
import com.family.finance.domain.model.NoteId
import com.family.finance.domain.model.Transaction
import com.family.finance.domain.model.TransactionId
import com.family.finance.domain.model.UserId
import com.family.finance.domain.repository.AccountRepository
import com.family.finance.domain.repository.CategoryRepository
import com.family.finance.domain.repository.NoteRepository
import com.family.finance.domain.repository.TransactionRepository
import com.family.finance.domain.sync.EditEntry
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.Duration

/** Documents that are present and not soft-deleted, decoded; unreadable ones are skipped. */
private fun <T> List<DocumentSnapshot>.live(decode: (String, Map<String, Any?>) -> T): List<T> =
    filter { it.exists() && it.getBoolean("deleted") != true }
        .mapNotNull { doc -> runCatching { decode(doc.id, doc.fields()) }.getOrNull() }

/** Document IDs marked deleted = true in a snapshot. */
private fun com.google.firebase.firestore.QuerySnapshot?.deletedIds(): List<String> =
    this?.documents.orEmpty().filter { it.getBoolean("deleted") == true }.map { it.id }

/** Stamp fields are rebuilt by RecordWriter; drop the stored ones before re-stamping. */
private fun Map<String, Any?>.withoutStamps() = this - setOf("version", "deleted", "updatedBy", "updatedAt")

class FirestoreTransactionRepository internal constructor(
    workspace: DocumentReference,
    private val writer: RecordWriter,
    private val me: () -> UserId,
    private val clock: Clock,
    private val errors: WriteErrors,
) : TransactionRepository {
    private val transactions = workspace.collection("transactions")
    private val edits = workspace.collection("edits")

    override fun observeDeletedIds(): Flow<Set<TransactionId>> =
        transactions.snapshots(errors::report).map { it.deletedIds().map(::TransactionId).toSet() }

    override fun observeAll(): Flow<List<Transaction>> =
        transactions.snapshots(errors::report).map { it?.documents.orEmpty().live(RecordMapping::decodeTransaction) }

    override suspend fun get(id: TransactionId): Transaction? {
        val existing = writer.existing(transactions.document(id.value)) ?: return null
        if (existing.fields["deleted"] == true) return null
        return runCatching { RecordMapping.decodeTransaction(id.value, existing.fields.fromFirestore()) }.getOrNull()
    }

    override suspend fun upsert(transaction: Transaction) {
        val ref = transactions.document(transaction.id.value)
        val existing = writer.existing(ref)
        val stored = writer.stamped(RecordMapping.encode(transaction), existing, clock.instant())
        writer.commit { batch ->
            batch.set(ref, stored)
            batch.set(edits.document(), logEntry(transaction.id, existing, stored))
        }
    }

    override suspend fun delete(id: TransactionId) {
        val ref = transactions.document(id.value)
        val existing = writer.existing(ref) ?: return
        if (existing.fields["deleted"] == true) return
        val stored = writer.stamped(existing.fields.withoutStamps(), existing, clock.instant(), deleted = true)
        writer.commit { batch ->
            batch.set(ref, stored)
            batch.set(edits.document(), logEntry(id, existing, stored))
        }
    }

    /** Recent saves only: conflicts come from short offline periods, not from years ago. */
    override fun observeEditLog(): Flow<Map<TransactionId, List<EditEntry>>> {
        val since = clock.instant().minus(EDIT_LOG_WINDOW).toFirestoreTimestamp()
        return edits.whereGreaterThan("editedAt", since).snapshots(errors::report).map { snapshot ->
            snapshot?.documents.orEmpty()
                .mapNotNull { doc -> runCatching { decodeEntry(doc.fields()) }.getOrNull() }
                .groupBy({ it.first }, { it.second })
        }
    }

    private fun logEntry(id: TransactionId, existing: RecordWriter.Existing?, stored: Map<String, Any?>) = mapOf(
        "transactionId" to id.value,
        "baseVersion" to (existing?.version ?: 0),
        "version" to stored["version"],
        "editedBy" to me().value,
        "editedAt" to stored["updatedAt"],
        "deleted" to stored["deleted"],
        "state" to stored,
    )

    @Suppress("UNCHECKED_CAST")
    private fun decodeEntry(d: Map<String, Any?>): Pair<TransactionId, EditEntry> {
        val id = TransactionId(d["transactionId"] as String)
        val deleted = d["deleted"] as Boolean? ?: false
        val state = (d["state"] as? Map<String, Any?>)?.takeUnless { deleted }
            ?.let { runCatching { RecordMapping.decodeTransaction(id.value, it) }.getOrNull() }
        return id to EditEntry(
            baseVersion = (d["baseVersion"] as Number).toInt(),
            version = (d["version"] as Number).toInt(),
            editedBy = UserId(d["editedBy"] as String),
            editedAt = d["editedAt"] as Instant,
            deleted = deleted,
            state = state,
        )
    }

    private companion object {
        val EDIT_LOG_WINDOW: Duration = Duration.ofDays(60)
    }
}

class FirestoreAccountRepository internal constructor(
    workspace: DocumentReference,
    private val writer: RecordWriter,
    private val clock: Clock,
    private val errors: WriteErrors,
) : AccountRepository {
    private val accounts = workspace.collection("accounts")

    override fun observeAll(): Flow<List<Account>> =
        accounts.snapshots(errors::report).map { it?.documents.orEmpty().live(RecordMapping::decodeAccount) }

    override suspend fun get(id: AccountId): Account? =
        writer.existing(accounts.document(id.value))?.let { runCatching { RecordMapping.decodeAccount(id.value, it.fields.fromFirestore()) }.getOrNull() }

    override suspend fun upsert(account: Account) {
        val ref = accounts.document(account.id.value)
        val stored = writer.stamped(RecordMapping.encode(account), writer.existing(ref), clock.instant())
        writer.commit { it.set(ref, stored) }
    }
}

/** Built-in categories live in the app; only custom ones and changes to built-ins are stored. */
class FirestoreCategoryRepository internal constructor(
    workspace: DocumentReference,
    private val writer: RecordWriter,
    private val clock: Clock,
    private val errors: WriteErrors,
) : CategoryRepository {
    private val categories = workspace.collection("categories")

    override fun observeAll(): Flow<List<Category>> =
        categories.snapshots(errors::report).map { snapshot ->
            val stored = snapshot?.documents.orEmpty().live(RecordMapping::decodeCategory).associateBy { it.id }
            DefaultCategories.all.map { stored[it.id] ?: it } + stored.values.filter { s -> DefaultCategories.all.none { it.id == s.id } }
        }

    override suspend fun get(id: CategoryId): Category? {
        val stored = writer.existing(categories.document(id.value))
            ?.let { runCatching { RecordMapping.decodeCategory(id.value, it.fields.fromFirestore()) }.getOrNull() }
        return stored ?: DefaultCategories.all.firstOrNull { it.id == id }
    }

    override suspend fun upsert(category: Category) {
        val ref = categories.document(category.id.value)
        val stored = writer.stamped(RecordMapping.encode(category), writer.existing(ref), clock.instant())
        writer.commit { it.set(ref, stored) }
    }
}

class FirestoreNoteRepository internal constructor(
    workspace: DocumentReference,
    private val writer: RecordWriter,
    private val clock: Clock,
    private val errors: WriteErrors,
) : NoteRepository {
    private val notes = workspace.collection("notes")

    override fun observeDeletedIds(): Flow<Set<NoteId>> =
        notes.snapshots(errors::report).map { it.deletedIds().map(::NoteId).toSet() }

    override fun observeAll(): Flow<List<NoteEntry>> =
        notes.snapshots(errors::report).map { it?.documents.orEmpty().live(RecordMapping::decodeNote) }

    override suspend fun get(id: NoteId): NoteEntry? {
        val existing = writer.existing(notes.document(id.value)) ?: return null
        if (existing.fields["deleted"] == true) return null
        return runCatching { RecordMapping.decodeNote(id.value, existing.fields.fromFirestore()) }.getOrNull()
    }

    override suspend fun upsert(note: NoteEntry) {
        val ref = notes.document(note.id.value)
        val stored = writer.stamped(RecordMapping.encode(note), writer.existing(ref), clock.instant())
        writer.commit { it.set(ref, stored) }
    }

    override suspend fun delete(id: NoteId) {
        val ref = notes.document(id.value)
        val existing = writer.existing(ref) ?: return
        val stored = writer.stamped(existing.fields.withoutStamps(), existing, clock.instant(), deleted = true)
        writer.commit { it.set(ref, stored) }
    }
}
