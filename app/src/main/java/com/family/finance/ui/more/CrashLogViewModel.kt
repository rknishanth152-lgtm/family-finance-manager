package com.family.finance.ui.more

import androidx.lifecycle.ViewModel
import com.family.finance.data.crash.CrashLogStore
import com.family.finance.data.crash.CrashReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.ZoneId

/** How many crashes are recorded, and the day of the latest one (in the phone's time zone). */
data class CrashSummary(val count: Int, val lastCrashDay: LocalDate?)

class CrashLogViewModel(
    private val store: CrashLogStore,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {

    private val _summary = MutableStateFlow(load())
    val summary: StateFlow<CrashSummary> = _summary.asStateFlow()

    fun clear() {
        store.clear()
        _summary.value = load()
    }

    private fun load(): CrashSummary {
        val log = store.read()
        return CrashSummary(CrashReport.count(log), CrashReport.lastCrashAt(log)?.atZone(zone())?.toLocalDate())
    }
}
