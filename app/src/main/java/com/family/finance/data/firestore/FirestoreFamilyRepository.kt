package com.family.finance.data.firestore

import com.family.finance.domain.model.Invite
import com.family.finance.domain.model.Member
import com.family.finance.domain.model.Role
import com.family.finance.domain.model.UserId
import com.family.finance.domain.model.Workspace
import com.family.finance.domain.model.WorkspaceId
import com.family.finance.domain.model.normalizeEmail
import com.family.finance.domain.repository.FamilyRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock

internal fun DocumentSnapshot.toMember(): Member? {
    if (!exists()) return null
    return Member(
        uid = UserId(id),
        email = getString("email").orEmpty(),
        displayName = getString("displayName").orEmpty(),
        role = if (getString("role") == "admin") Role.ADMIN else Role.MEMBER,
        active = getString("status") == "active",
    )
}

class FirestoreFamilyRepository internal constructor(
    private val db: FirebaseFirestore,
    override val workspace: Workspace,
    override val me: Member,
    private val clock: Clock,
    private val errors: WriteErrors,
) : FamilyRepository {
    private val workspaceRef = db.collection("workspaces").document(workspace.id.value)

    override fun observeMembers(): Flow<List<Member>> =
        workspaceRef.collection("members").snapshots(errors::report).map { snapshot ->
            snapshot?.documents.orEmpty().mapNotNull { it.toMember() }.filter { it.active }
                .sortedWith(compareBy<Member> { it.role != Role.ADMIN }.thenBy { it.displayName.lowercase() })
        }

    /** Only the admin can list invites (rules); others simply see none. */
    override fun observeInvites(): Flow<List<Invite>> =
        db.collection("invites").whereEqualTo("workspaceId", workspace.id.value).snapshots().map { snapshot ->
            snapshot?.documents.orEmpty().map { Invite(it.id, WorkspaceId(workspace.id.value)) }.sortedBy { it.email }
        }

    override suspend fun invite(email: String) {
        val normalized = normalizeEmail(email)
        db.collection("invites").document(normalized).set(
            mapOf(
                "workspaceId" to workspace.id.value,
                "workspaceName" to workspace.name,
                "invitedByName" to me.displayName,
                "role" to "member",
                "invitedBy" to me.uid.value,
                "createdAt" to clock.instant().toFirestoreTimestamp(),
            ),
        ).addOnFailureListener(errors::report)
    }

    override suspend fun cancelInvite(email: String) {
        db.collection("invites").document(normalizeEmail(email)).delete().addOnFailureListener(errors::report)
    }

    override suspend fun removeMember(uid: UserId) {
        if (uid == me.uid) return
        workspaceRef.collection("members").document(uid.value).update("status", "removed")
            .addOnFailureListener(errors::report)
    }
}
