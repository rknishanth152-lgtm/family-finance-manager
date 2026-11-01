package com.family.finance.testing

import com.family.finance.data.AppContainer
import com.family.finance.domain.model.Account
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.AccountType
import com.family.finance.domain.model.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Replaces Dispatchers.Main so viewModelScope runs in unit tests. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

/** A clock that only moves when told to. */
class MutableClock(var now: Instant, private val zone: ZoneId = ZoneOffset.UTC) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
    override fun instant(): Instant = now
}

val TODAY: LocalDate = LocalDate.parse("2026-03-15")

fun testContainer(clock: MutableClock = MutableClock(TODAY.atTime(10, 0).toInstant(ZoneOffset.UTC))) =
    AppContainer(clock = clock)

fun account(id: String, opening: Long = 0, active: Boolean = true) = Account(
    id = AccountId(id),
    name = id,
    type = AccountType.CASH,
    openingBalance = Money.ofRupees(opening),
    openingBalanceDate = LocalDate.parse("2026-01-01"),
    isActive = active,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
)
