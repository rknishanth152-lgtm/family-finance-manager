package com.family.finance.domain.sync

/** What the user is told about syncing (PRD 4.5, 11.2). */
enum class SyncState { SYNCED, PENDING, SYNCING, OFFLINE, ERROR }

object SyncStatus {
    /**
     * Never claims "synced" while local changes are unconfirmed: [hasPendingWrites] is true
     * until the server acknowledges every local write.
     *
     * @param online the phone has a working internet connection
     * @param hasPendingWrites local changes not yet confirmed by the cloud
     * @param fromCache what is shown came from the phone's copy, not yet checked with the cloud
     * @param lastWriteFailed the cloud rejected a change
     */
    fun of(online: Boolean, hasPendingWrites: Boolean, fromCache: Boolean, lastWriteFailed: Boolean): SyncState = when {
        lastWriteFailed -> SyncState.ERROR
        !online -> if (hasPendingWrites) SyncState.PENDING else SyncState.OFFLINE
        hasPendingWrites || fromCache -> SyncState.SYNCING
        else -> SyncState.SYNCED
    }
}
