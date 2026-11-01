package com.family.finance.ui.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The Material date picker works in UTC milliseconds. Converting through UTC on both sides
 * keeps the chosen calendar date exact in every timezone (PRD 8.5).
 */
object PickerDates {
    fun toMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun fromMillis(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
}
