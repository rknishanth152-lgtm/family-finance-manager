package com.family.finance.data.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

/** Instant <-> Firestore Timestamp, applied to whole field maps (including nested ones). */
internal fun Map<String, Any?>.toFirestore(): Map<String, Any?> = mapValues { (_, v) -> v.toFirestoreValue() }

private fun Any?.toFirestoreValue(): Any? = when (this) {
    is Instant -> toFirestoreTimestamp()
    is Map<*, *> -> entries.associate { (k, v) -> k.toString() to v.toFirestoreValue() }
    else -> this
}

internal fun Map<String, Any?>.fromFirestore(): Map<String, Any?> = mapValues { (_, v) -> v.fromFirestoreValue() }

private fun Any?.fromFirestoreValue(): Any? = when (this) {
    is Timestamp -> Instant.ofEpochSecond(seconds, nanoseconds.toLong())
    is Map<*, *> -> entries.associate { (k, v) -> k.toString() to v.fromFirestoreValue() }
    else -> this
}

internal fun DocumentSnapshot.fields(): Map<String, Any?> = (data ?: emptyMap()).fromFirestore()

/**
 * Live query results. If the cloud refuses or drops the listener (e.g. a read made a moment
 * before membership reached the cloud, or a removed member), null is emitted and the
 * listener is set up again after a growing pause, instead of staying silent for good.
 */
internal fun Query.snapshots(onError: (Exception) -> Unit = {}): Flow<QuerySnapshot?> = callbackFlow {
    val registration = addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
        if (error != null) {
            onError(error)
            trySend(null)
            close(error)
        } else {
            trySend(snapshot)
        }
    }
    awaitClose { registration.remove() }
}.retryWithBackoff()

internal fun DocumentReference.snapshots(onError: (Exception) -> Unit = {}): Flow<DocumentSnapshot?> = callbackFlow {
    val registration = addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
        if (error != null) {
            onError(error)
            trySend(null)
            close(error)
        } else {
            trySend(snapshot)
        }
    }
    awaitClose { registration.remove() }
}.retryWithBackoff()

/** Re-subscribes after 1, 2, 4 ... up to 30 seconds; never gives up while someone is listening. */
private fun <T> Flow<T>.retryWithBackoff(): Flow<T> = retryWhen { _, attempt ->
    delay(minOf(MAX_RETRY_MS, 1_000L shl attempt.coerceAtMost(5L).toInt()))
    true
}

private const val MAX_RETRY_MS = 30_000L

/** Collects write failures so the sync indicator can say "sync problem" (PRD 9.6, 11.2). */
class WriteErrors {
    val failed = MutableStateFlow(false)
    fun report(error: Exception?) { failed.value = error != null }
}

/**
 * Shared write path for family records:
 * - the version, creator and creation time of an existing record are carried over
 *   (the rules reject any change to creator/creation time);
 * - writes are committed without waiting for the network. Firestore applies them to the
 *   phone's copy at once and syncs later; offline writes survive app restarts (PRD 4.5, 11.3).
 */
internal class RecordWriter(
    private val db: FirebaseFirestore,
    private val me: () -> String,
    private val errors: WriteErrors,
) {
    data class Existing(val version: Int, val createdBy: String?, val createdAt: Any?, val fields: Map<String, Any?>)

    /** Reads the phone's copy first; asks the server only if the record is not cached yet. */
    suspend fun existing(ref: DocumentReference): Existing? {
        val snapshot = runCatching { ref.get(Source.CACHE).await() }.getOrNull()
            ?: withTimeoutOrNull(SERVER_LOOKUP_MS) { runCatching { ref.get(Source.SERVER).await() }.getOrNull() }
        if (snapshot == null || !snapshot.exists()) return null
        val raw = snapshot.data ?: return null
        return Existing(
            version = (raw["version"] as? Number)?.toInt() ?: 0,
            createdBy = raw["createdBy"] as String?,
            createdAt = raw["createdAt"],
            fields = raw,
        )
    }

    /** Builds the stored fields: domain fields + stamps required by the rules. */
    fun stamped(fields: Map<String, Any?>, existing: Existing?, now: Instant, deleted: Boolean = false): Map<String, Any?> {
        val out = fields.toFirestore().toMutableMap()
        out["version"] = (existing?.version ?: 0) + 1
        out["deleted"] = deleted
        out["updatedBy"] = me()
        out["updatedAt"] = now.toFirestoreTimestamp()
        out["createdBy"] = existing?.createdBy ?: (fields["createdBy"] ?: me())
        out["createdAt"] = existing?.createdAt ?: (fields["createdAt"] as? Instant)?.toFirestoreTimestamp() ?: now.toFirestoreTimestamp()
        return out
    }

    fun commit(build: (WriteBatch) -> Unit) {
        val batch = db.batch()
        build(batch)
        batch.commit()
            .addOnSuccessListener { errors.report(null) }
            .addOnFailureListener { errors.report(it) }
    }

    private companion object {
        const val SERVER_LOOKUP_MS = 3_000L
    }
}

internal fun Instant.toFirestoreTimestamp() = Timestamp(epochSecond, nano)
