package com.family.finance.data.firestore

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.family.finance.domain.sync.SyncState
import com.family.finance.domain.sync.SyncStatus
import com.google.firebase.firestore.DocumentReference
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The "Synced / Waiting / Offline / Problem" indicator (PRD 6.1 item 14, 11.2). */
internal class SyncMonitor(private val context: Context, workspace: DocumentReference, errors: WriteErrors) {

    private data class Local(val pending: Boolean, val fromCache: Boolean)

    private val local: Flow<Local> = combine(
        listOf("transactions", "accounts", "categories", "notes", "edits").map { name ->
            workspace.collection(name).snapshots().map { s ->
                Local(pending = s?.metadata?.hasPendingWrites() ?: false, fromCache = s?.metadata?.isFromCache ?: true)
            }
        },
    ) { all -> Local(all.any { it.pending }, all.any { it.fromCache }) }

    val state: Flow<SyncState> = combine(online(), local, errors.failed) { online, local, failed ->
        SyncStatus.of(online, local.pending, local.fromCache, failed)
    }.distinctUntilChanged()

    private fun online(): Flow<Boolean> = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        fun current(): Boolean = manager.getNetworkCapabilities(manager.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(current()) }
            override fun onLost(network: Network) { trySend(current()) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { trySend(current()) }
        }
        trySend(current())
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        manager.registerNetworkCallback(request, callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
