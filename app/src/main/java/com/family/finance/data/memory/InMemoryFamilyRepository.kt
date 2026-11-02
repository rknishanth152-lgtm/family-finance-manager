package com.family.finance.data.memory

import com.family.finance.domain.model.Invite
import com.family.finance.domain.model.Member
import com.family.finance.domain.model.Role
import com.family.finance.domain.model.UserId
import com.family.finance.domain.model.Workspace
import com.family.finance.domain.model.WorkspaceId
import com.family.finance.domain.model.normalizeEmail
import com.family.finance.domain.repository.FamilyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Single-phone stand-in for unit tests and screenshots. */
class InMemoryFamilyRepository(
    override val workspace: Workspace = Workspace(WorkspaceId("local"), "Family"),
    override val me: Member = Member(UserId("local-user"), "you@example.com", "You", Role.ADMIN, active = true),
) : FamilyRepository {
    private val members = MutableStateFlow(listOf(me))
    private val invites = MutableStateFlow<List<Invite>>(emptyList())

    override fun observeMembers(): Flow<List<Member>> = members
    override fun observeInvites(): Flow<List<Invite>> = invites

    override suspend fun invite(email: String) {
        val normalized = normalizeEmail(email)
        invites.update { list -> list.filterNot { it.email == normalized } + Invite(normalized, workspace.id) }
    }

    override suspend fun cancelInvite(email: String) {
        invites.update { list -> list.filterNot { it.email == normalizeEmail(email) } }
    }

    override suspend fun removeMember(uid: UserId) {
        if (uid == me.uid) return
        members.update { list -> list.map { if (it.uid == uid) it.copy(active = false) else it } }
    }
}
