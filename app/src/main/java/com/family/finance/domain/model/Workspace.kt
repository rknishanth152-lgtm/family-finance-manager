package com.family.finance.domain.model

@JvmInline
value class WorkspaceId(val value: String)

enum class Role { ADMIN, MEMBER }

/** The signed-in person (PRD 10.1). */
data class SignedInUser(val uid: UserId, val email: String, val displayName: String)

/** A person in the family workspace (PRD 10.2). */
data class Member(
    val uid: UserId,
    val email: String,
    val displayName: String,
    val role: Role,
    val active: Boolean,
)

data class Workspace(val id: WorkspaceId, val name: String)

/** Someone the admin has invited who has not signed in yet. */
data class Invite(val email: String, val workspaceId: WorkspaceId)

/** Emails are compared case-insensitively everywhere (rules use the same form). */
fun normalizeEmail(email: String): String = email.trim().lowercase()
