package com.family.finance.data

/** What the app should show for one family, from what the phone knows about the membership. */
enum class MembershipView { LOADING, NEEDS_FAMILY, READY }

/**
 * Decides whether this phone may open the family. Kept free of Firebase types so the rules
 * are unit-tested.
 *
 * A removed member can no longer read their own membership record (the rules allow it only
 * to active members), so the "removed" status itself never reaches their phone. The cloud
 * refusing that read is therefore treated as removal; otherwise the phone would keep
 * reopening the family from its offline copy of the old "active" record.
 */
object MembershipDecision {

    fun decide(
        /** The membership and family records are both known (from the cloud or the offline copy). */
        known: Boolean,
        /** What is known came only from the offline copy. */
        fromCache: Boolean,
        /** Created or joined on this phone and not yet confirmed by the cloud. */
        pendingWrites: Boolean,
        /** The cloud refused to let this person read the family since its last confirmation. */
        refused: Boolean,
        active: Boolean,
    ): MembershipView = when {
        !known -> if (refused) MembershipView.NEEDS_FAMILY else if (fromCache) MembershipView.LOADING else MembershipView.NEEDS_FAMILY
        // Opening before the cloud has the new membership would make its first reads fail.
        pendingWrites -> MembershipView.LOADING
        refused -> MembershipView.NEEDS_FAMILY
        !active -> MembershipView.NEEDS_FAMILY
        else -> MembershipView.READY
    }
}
