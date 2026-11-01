package com.family.finance.domain.sync

import com.family.finance.domain.model.Transaction
import com.family.finance.domain.model.UserId
import java.time.Instant

/**
 * One saved version of a transaction. Every save is logged, so if both phones edit the
 * same entry nothing is silently lost (PRD 9.4, 11.5).
 *
 * @param baseVersion the version the editor was looking at when they started editing
 * @param version the version this save produced
 * @param state what was saved (null when the save was a delete)
 */
data class EditEntry(
    val baseVersion: Int,
    val version: Int,
    val editedBy: UserId,
    val editedAt: Instant,
    val deleted: Boolean,
    val state: Transaction?,
)

object ConflictDetector {
    /**
     * Two saves that started from the same version were made without seeing each other
     * (e.g. both phones offline). The conflict stays open until someone saves again after
     * seeing the merged result, which starts a newer round.
     */
    fun competingEdits(history: List<EditEntry>): List<EditEntry> {
        if (history.isEmpty()) return emptyList()
        val latestRound = history.maxOf { it.baseVersion }
        val round = history.filter { it.baseVersion == latestRound }
        return if (round.size > 1) round.sortedBy { it.editedAt } else emptyList()
    }

    fun hasConflict(history: List<EditEntry>): Boolean = competingEdits(history).isNotEmpty()

    /** Version for the next save, given the version the editor is looking at. */
    fun nextVersion(currentVersion: Int): Int = currentVersion + 1
}
