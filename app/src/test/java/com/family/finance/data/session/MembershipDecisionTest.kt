package com.family.finance.data.session

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MembershipDecisionTest {

    private fun decide(
        known: Boolean = true, fromCache: Boolean = false, pendingWrites: Boolean = false,
        refused: Boolean = false, active: Boolean = true,
    ) = MembershipDecision.decide(known, fromCache, pendingWrites, refused, active)

    @Test fun `an active member confirmed by the cloud opens the family`() {
        assertThat(decide()).isEqualTo(MembershipView.READY)
    }

    @Test fun `offline, the saved membership still opens the family`() {
        assertThat(decide(fromCache = true)).isEqualTo(MembershipView.READY)
    }

    @Test fun `a removed member is shut out even though the phone still has the old active record`() {
        assertThat(decide(fromCache = true, refused = true)).isEqualTo(MembershipView.NEEDS_FAMILY)
        assertThat(decide(known = false, refused = true)).isEqualTo(MembershipView.NEEDS_FAMILY)
    }

    @Test fun `a membership marked removed is shut out`() {
        assertThat(decide(active = false)).isEqualTo(MembershipView.NEEDS_FAMILY)
    }

    @Test fun `a family just created on this phone waits for the cloud before opening`() {
        assertThat(decide(pendingWrites = true)).isEqualTo(MembershipView.LOADING)
    }

    @Test fun `nothing known yet waits only while reading the offline copy`() {
        assertThat(decide(known = false, fromCache = true)).isEqualTo(MembershipView.LOADING)
        assertThat(decide(known = false, fromCache = false)).isEqualTo(MembershipView.NEEDS_FAMILY)
    }
}
