package com.family.finance

import androidx.test.platform.app.InstrumentationRegistry
import com.family.finance.di.AppContainer

/**
 * For UI tests that don't exercise Firebase: puts a fresh in-memory family in place of
 * sign-in, so the app opens straight to Home. Install before the activity starts.
 */
object TestSession {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as FamilyFinanceApplication

    fun install(): AppContainer {
        val container = AppContainer()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { app.session.testOverride.value = container }
        return container
    }

    val current: AppContainer get() = checkNotNull(app.session.testOverride.value) { "TestSession.install() not called" }
}
