package com.family.finance.data.crash

import com.family.finance.ui.more.CrashLogViewModel
import com.family.finance.ui.more.CrashSummary
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CrashReportTest {

    private val details = listOf("app" to "0.1.0 (1)", "android" to "15 (API 35)", "phone" to "Google Pixel 8")

    private fun entry(at: String, error: Throwable = IllegalStateException("boom")) =
        CrashReport.format(Instant.parse(at), details, "main", error)

    @Test fun `an entry says when, where and what failed, including the cause`() {
        val text = entry("2026-11-02T10:15:30Z", RuntimeException("outer", IllegalArgumentException("inner")))
        assertThat(text).contains("time: 2026-11-02T10:15:30Z")
        assertThat(text).contains("phone: Google Pixel 8")
        assertThat(text).contains("thread: main")
        assertThat(text).contains("java.lang.RuntimeException: outer")
        assertThat(text).contains("caused by: java.lang.IllegalArgumentException: inner")
        assertThat(text).contains("    at com.family.finance.data.crash.CrashReportTest")
    }

    @Test fun `long error messages are cut short and kept on one line`() {
        val text = entry("2026-11-02T10:15:30Z", IllegalStateException("x".repeat(5_000) + "\nsecond line"))
        val line = text.lines().first { it.startsWith("java.lang.IllegalStateException") }
        assertThat(line.length).isLessThan(300)
        assertThat(text).doesNotContain("second line")
    }

    @Test fun `only the latest crashes are kept, and the latest time is found`() {
        var log = ""
        for (day in 1..15) log = CrashReport.append(log, entry("2026-11-%02dT08:00:00Z".format(day)))
        assertThat(CrashReport.count(log)).isEqualTo(CrashReport.MAX_ENTRIES)
        assertThat(CrashReport.lastCrashAt(log)).isEqualTo(Instant.parse("2026-11-15T08:00:00Z"))
        assertThat(log).doesNotContain("2026-11-05T08:00:00Z")
        assertThat(log).contains("2026-11-06T08:00:00Z")
    }

    @Test fun `an empty log has no crashes`() {
        assertThat(CrashReport.count("")).isEqualTo(0)
        assertThat(CrashReport.lastCrashAt("")).isNull()
    }

    @Test fun `the More screen summary uses the phone's own date, and clearing empties it`() {
        val store = object : CrashLogStore {
            var text = CrashReport.append("", entry("2026-11-02T20:00:00Z"))
            override fun read() = text
            override fun clear() { text = "" }
        }
        val vm = CrashLogViewModel(store, zone = { ZoneId.of("Asia/Kolkata") })
        // 20:00 UTC is already the next day in India.
        assertThat(vm.summary.value).isEqualTo(CrashSummary(1, LocalDate.parse("2026-11-03")))
        vm.clear()
        assertThat(vm.summary.value).isEqualTo(CrashSummary(0, null))
    }
}
