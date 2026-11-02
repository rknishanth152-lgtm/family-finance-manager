package com.family.finance.data.session

import android.content.Context
import com.family.finance.data.firestore.FirestoreAccountRepository
import com.family.finance.data.firestore.FirestoreCategoryRepository
import com.family.finance.data.firestore.FirestoreFamilyRepository
import com.family.finance.data.firestore.FirestoreNoteRepository
import com.family.finance.data.firestore.FirestoreTransactionRepository
import com.family.finance.data.firestore.RecordWriter
import com.family.finance.data.firestore.SyncMonitor
import com.family.finance.data.firestore.WriteErrors
import com.family.finance.data.firestore.snapshots
import com.family.finance.data.firestore.toFirestoreTimestamp
import com.family.finance.data.firestore.toMember
import com.family.finance.domain.model.Member
import com.family.finance.domain.model.SignedInUser
import com.family.finance.domain.model.UserId
import com.family.finance.domain.model.Workspace
import com.family.finance.domain.model.WorkspaceId
import com.family.finance.domain.model.normalizeEmail
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import java.time.Clock
import com.family.finance.di.AppContainer

/** An invite waiting for the signed-in person, shown on the "join your family" screen. */
data class PendingInvite(val workspaceId: WorkspaceId, val workspaceName: String, val invitedByName: String)

/** This Google account is not on the list of people allowed to start a family. */
class NotAllowedToCreateFamily : Exception()

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class NeedsFamily(val user: SignedInUser, val invite: PendingInvite?) : SessionState
    data class Ready(val container: AppContainer) : SessionState
}

/**
 * Signed out -> sign in with Google -> create or join the family -> the app (PRD 3.3, 10).
 * Builds one AppContainer of Firestore repositories per (person, family).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionManager(
    private val context: Context,
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val clock: Clock = Clock.systemDefaultZone(),
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    /** Instrumented UI tests that don't exercise Firebase put an in-memory container here. */
    val testOverride = MutableStateFlow<AppContainer?>(null)

    private data class Key(val user: SignedInUser, val workspace: Workspace, val me: Member)

    // Declared before the flows below: `state` starts collecting during construction.
    private val inviteRefresh = MutableStateFlow(0)
    private var cached: Pair<Key, AppContainer>? = null

    private val firebaseState: Flow<SessionState> = authUser().flatMapLatest { user ->
        if (user == null) flowOf(SessionState.SignedOut) else familyOf(user)
    }

    val state: StateFlow<SessionState> = combine(testOverride, firebaseState) { override, firebase ->
        if (override != null) SessionState.Ready(override) else firebase
    }.stateIn(scope, SharingStarted.Eagerly, SessionState.Loading)

    // --- actions ---

    /** Exchanges the Google ID token (from Credential Manager in the UI) for a Firebase sign-in. */
    suspend fun signInWithGoogle(idToken: String) {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
    }

    /** The founder becomes the admin. Workspace, membership and pointer are written together. */
    suspend fun createFamily(name: String) {
        val user = currentUser() ?: return
        val workspace = db.collection("workspaces").document()
        val now = clock.instant().toFirestoreTimestamp()
        try {
            db.batch().apply {
                set(workspace, mapOf("name" to name.trim(), "createdBy" to user.uid.value, "createdAt" to now, "schemaVersion" to 1L))
                set(workspace.collection("members").document(user.uid.value), memberFields(user, "admin", now))
                set(db.collection("users").document(user.uid.value), mapOf("workspaceId" to workspace.id, "updatedAt" to now))
            }.commit().await()
        } catch (e: FirebaseFirestoreException) {
            // Only Google accounts on the founders allowlist may start a family (firestore.rules).
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) throw NotAllowedToCreateFamily()
            throw e
        }
    }

    /** The invited person joins as a member and uses up the invite. */
    suspend fun joinFamily(invite: PendingInvite) {
        val user = currentUser() ?: return
        val now = clock.instant().toFirestoreTimestamp()
        val workspace = db.collection("workspaces").document(invite.workspaceId.value)
        db.batch().apply {
            set(workspace.collection("members").document(user.uid.value), memberFields(user, "member", now))
            set(db.collection("users").document(user.uid.value), mapOf("workspaceId" to invite.workspaceId.value, "updatedAt" to now))
            delete(db.collection("invites").document(user.email))
        }.commit().await()
    }

    /** Looks for an invite again (e.g. after the admin just added this email). */
    fun refreshInvite() { inviteRefresh.value++ }

    fun signOut() {
        cached = null
        auth.signOut()
    }

    // --- state plumbing ---

    private fun currentUser(): SignedInUser? = auth.currentUser?.let {
        SignedInUser(UserId(it.uid), normalizeEmail(it.email.orEmpty()), it.displayName.orEmpty())
    }

    private fun authUser(): Flow<SignedInUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(currentUser()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    private fun familyOf(user: SignedInUser): Flow<SessionState> =
        db.collection("users").document(user.uid.value).snapshots().flatMapLatest { pointer ->
            val workspaceId = pointer?.getString("workspaceId")
            if (workspaceId == null) {
                // Wait for the server before deciding "no family": an empty cache is not an answer.
                if (pointer == null || pointer.metadata.isFromCache) flowOf(SessionState.Loading) else needsFamily(user)
            } else {
                membership(user, WorkspaceId(workspaceId))
            }
        }

    private fun membership(user: SignedInUser, workspaceId: WorkspaceId): Flow<SessionState> {
        val ref = db.collection("workspaces").document(workspaceId.value)
        // Set when the cloud refuses to let this person read the family (see MembershipDecision);
        // cleared again by any membership snapshot the cloud itself confirms.
        val refused = MutableStateFlow(false)
        val onError: (Exception) -> Unit = {
            if ((it as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) refused.value = true
        }
        val member = ref.collection("members").document(user.uid.value).snapshots(onError)
            .onEach { if (it != null && !it.metadata.isFromCache) refused.value = false }
        return combine(member, ref.snapshots(onError), refused) { member, workspace, denied ->
            val me = member?.toMember()
            val known = me != null && workspace != null && workspace.exists()
            val view = MembershipDecision.decide(
                known = known,
                fromCache = member?.metadata?.isFromCache != false,
                pendingWrites = known && (member!!.metadata.hasPendingWrites() || workspace!!.metadata.hasPendingWrites()),
                refused = denied,
                active = me?.active == true,
            )
            when (view) {
                MembershipView.LOADING -> SessionState.Loading
                MembershipView.NEEDS_FAMILY -> null
                MembershipView.READY -> SessionState.Ready(containerFor(Key(user, Workspace(workspaceId, workspace!!.getString("name").orEmpty()), me!!)))
            }
        }.flatMapLatest { state -> if (state == null) needsFamily(user) else flowOf(state) }
    }

    /** Listens for an invite, so the screen updates by itself when the admin invites this email. */
    private fun needsFamily(user: SignedInUser): Flow<SessionState> = inviteRefresh.flatMapLatest {
        db.collection("invites").document(user.email).snapshots().map { doc ->
            val invite = doc?.takeIf { it.exists() }?.let {
                PendingInvite(
                    WorkspaceId(it.getString("workspaceId").orEmpty()),
                    it.getString("workspaceName").orEmpty(),
                    it.getString("invitedByName").orEmpty(),
                )
            }
            SessionState.NeedsFamily(user, invite)
        }
    }

    private fun containerFor(key: Key): AppContainer {
        cached?.let { (k, c) -> if (k == key) return c }
        val errors = WriteErrors()
        val uid = { key.user.uid }
        val writer = RecordWriter(db, { key.user.uid.value }, errors)
        val ws = db.collection("workspaces").document(key.workspace.id.value)
        val container = AppContainer(
            clock = clock,
            currentUser = uid,
            transactionRepository = FirestoreTransactionRepository(ws, writer, uid, clock, errors),
            accountRepository = FirestoreAccountRepository(ws, writer, clock, errors),
            categoryRepository = FirestoreCategoryRepository(ws, writer, clock, errors),
            noteRepository = FirestoreNoteRepository(ws, writer, clock, errors),
            familyRepository = FirestoreFamilyRepository(db, key.workspace, key.me, clock, errors),
            syncState = SyncMonitor(context, ws, errors).state,
            signOut = ::signOut,
        )
        cached = key to container
        return container
    }

    private fun memberFields(user: SignedInUser, role: String, now: Any) = mapOf(
        "role" to role,
        "status" to "active",
        "email" to user.email,
        "displayName" to user.displayName,
        "createdAt" to now,
    )

}
