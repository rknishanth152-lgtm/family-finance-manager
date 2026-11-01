package com.family.finance.ui.common

import com.family.finance.domain.model.Money
import com.family.finance.domain.model.MoneyFormatter
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.util.TimeZone

class PickerDatesTest {

    @Test fun `picked date never shifts, whatever the device timezone`() {
        val original = TimeZone.getDefault()
        try {
            listOf("Asia/Kolkata", "America/Los_Angeles", "Pacific/Kiritimati", "UTC").forEach { zone ->
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                listOf("2026-03-15", "2026-01-01", "2028-02-29", "2026-12-31").forEach {
                    val date = LocalDate.parse(it)
                    assertThat(PickerDates.fromMillis(PickerDates.toMillis(date))).isEqualTo(date)
                }
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test fun `editable amount text round-trips through parse`() {
        listOf(Money(0), Money(5), Money(125_050), Money(-250), Money.ofRupees(20_000)).forEach {
            assertThat(Money.parse(MoneyFormatter.plain(it))).isEqualTo(it)
        }
        assertThat(MoneyFormatter.plain(Money(125_050))).isEqualTo("1250.50")
    }
}
