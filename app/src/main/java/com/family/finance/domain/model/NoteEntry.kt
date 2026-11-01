package com.family.finance.domain.model

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@JvmInline
value class NoteId(val value: String) {
    companion object {
        fun generate() = NoteId(UUID.randomUUID().toString())
    }
}

/** A notebook entry (PRD 6.9). Never affects any balance. */
data class NoteEntry(
    val id: NoteId,
    val title: String,
    val description: String,
    val date: LocalDate,
    val createdBy: UserId,
    val createdAt: Instant,
    val updatedAt: Instant,
)
