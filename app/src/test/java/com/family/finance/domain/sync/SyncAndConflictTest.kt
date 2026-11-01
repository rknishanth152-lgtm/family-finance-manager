package com.family.finance.domain.sync

import com.family.finance.domain.Fixtures.CASH_ID
import com.family.finance.domain.Fixtures.expense
import com.family.finance.domain.Fixtures.rupees
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.DefaultCategories
import com.family.finance.domain.model.TransactionId
import com.family.finance.domain.model.UserId
import com.family.finance.domain.validation.ExpenseDraft
import com.family.finance.testing.TODAY
import com.family.finance.testing.account
import com.family.finance.testing.testContainer
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class SyncAndConflictTest {

    // --- sync status shown to the user (PRD 4.5, 11.2) ---

    @Test fun `synced only when online, nothing pending and confirmed by the server`() {
        assertThat(SyncStatus.of(online = true, hasPendingWrites = false, fromCache = false, lastWriteFailed = false))
            .isEqualTo(SyncState.SYNCED)
    }

    @Test fun `local changes never show as synced before the cloud confirms them`() {
        assertThat(SyncStatus.of(online = true, hasPendingWrites = true, fromCache = false, lastWriteFailed = false))
            .isEqualTo(SyncState.SYNCING)
        assertThat(SyncStatus.of(online = false, hasPendingWrites = true, fromCache = true, lastWriteFailed = false))
            .isEqualTo(SyncState.PENDING)
    }

    @Test fun `offline with nothing waiting says offline`() {
        assertThat(SyncStatus.of(online = false, hasPendingWrites = false, fromCache = true, lastWriteFailed = false))
            .isEqualTo(SyncState.OFFLINE)
    }

    @Test fun `a rejected change is reported as an error above everything else`() {
        assertThat(SyncStatus.of(online = true, hasPendingWrites = false, fromCache = false, lastWriteFailed = true))
            .isEqualTo(SyncState.ERROR)
        assertThat(SyncStatus.of(online = false, hasPendingWrites = true, fromCache = true, lastWriteFailed = true))
            .isEqualTo(SyncState.ERROR)
    }

    // --- both phones editing the same entry (PRD 9.4, 11.5) ---

    private val father = UserId("father")
    private val mother = UserId("mother")
    private fun entry(base: Int, version: Int, by: UserId, minute: Long, amount: Long = 100) = EditEntry(
        baseVersion = base, version = version, editedBy = by,
        editedAt = Instant.parse("2026-11-01T10:00:00Z").plusSeconds(minute * 60),
        deleted = false, state = expense(rupees(amount), CASH_ID),
    )

    @Test fun `edits one after another are not a conflict`() {
        val history = listOf(entry(0, 1, father, 0), entry(1, 2, mother, 5), entry(2, 3, father, 9))
        assertThat(ConflictDetector.hasConflict(history)).isFalse()
    }

    @Test fun `two edits from the same version are a conflict and both are kept`() {
        val fathers = entry(1, 2, father, 5, amount = 2650)
        val mothers = entry(1, 2, mother, 7, amount = 2800)
        val history = listOf(entry(0, 1, father, 0), mothers, fathers)
        assertThat(ConflictDetector.hasConflict(history)).isTrue()
        assertThat(ConflictDetector.competingEdits(history)).containsExactly(fathers, mothers).inOrder()
    }

    @Test fun `a later save after seeing both versions resolves the conflict`() {
        val history = listOf(entry(0, 1, father, 0), entry(1, 2, father, 5), entry(1, 2, mother, 7), entry(2, 3, mother, 20))
        assertThat(ConflictDetector.hasConflict(history)).isFalse()
    }

    @Test fun `a delete on one phone and an edit on the other is also a conflict`() {
        val deleted = entry(1, 2, mother, 6).copy(deleted = true, state = null)
        val history = listOf(entry(0, 1, father, 0), entry(1, 2, father, 5), deleted)
        assertThat(ConflictDetector.hasConflict(history)).isTrue()
    }

    @Test fun `no history means no conflict`() {
        assertThat(ConflictDetector.hasConflict(emptyList())).isFalse()
    }

    // --- every save is logged; confirming starts a new round ---

    @Test fun `each save and delete is logged with increasing versions`() = runTest {
        val c = testContainer()
        c.accountRepository.upsert(account("cash"))
        val id = TransactionId("e1")
        val draft = ExpenseDraft("100", "Tea", DefaultCategories.idFor("food"), TODAY, AccountId("cash"))
        c.transactionService.saveExpense(id, draft)
        c.transactionService.saveExpense(id, draft.copy(amountText = "120"))
        c.transactionService.delete(id)

        val log = c.transactionRepository.observeEditLog().first().getValue(id)
        assertThat(log.map { it.baseVersion to it.version }).containsExactly(0 to 1, 1 to 2, 2 to 3).inOrder()
        assertThat(log.last().deleted).isTrue()
        assertThat(log.first().editedBy).isEqualTo(c.currentUser())
    }

    @Test fun `confirming a version saves it again without changing it`() = runTest {
        val c = testContainer()
        c.accountRepository.upsert(account("cash"))
        val id = TransactionId("e1")
        c.transactionService.saveExpense(id, ExpenseDraft("100", "Tea", DefaultCategories.idFor("food"), TODAY, AccountId("cash")))
        val before = c.transactionRepository.get(id)!!
        c.transactionService.confirm(id)

        assertThat(c.transactionRepository.get(id)!!.amount).isEqualTo(before.amount)
        assertThat(c.transactionRepository.observeEditLog().first().getValue(id)).hasSize(2)
    }
}
