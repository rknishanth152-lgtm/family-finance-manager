package com.family.finance

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.core.os.LocaleListCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.AccountType
import com.family.finance.domain.validation.AccountDraft
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

/**
 * Runs on a device or emulator, in Tamil (the app default). Strings come from the
 * activity's resources, so the assertions follow whatever language is active.
 */
class AppFlowsTest {

    /**
     * Before the activity starts: pin the app language (so the first launch does not
     * recreate the activity mid-test) and make sure one account exists.
     */
    private val setUp = object : ExternalResource() {
        override fun before() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("ta")
            } else {
                instrumentation.runOnMainSync {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ta"))
                }
            }
            val container = (context.applicationContext as FamilyFinanceApplication).container
            runBlocking {
                container.accountService.save(
                    AccountId("ui-test-cash"),
                    AccountDraft(ACCOUNT_NAME, AccountType.CASH, "100", container.today()),
                )
            }
        }
    }

    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(setUp).around(compose)

    private fun str(id: Int) = compose.activity.getString(id)

    @Test fun bottomNavigationOpensEveryTab() {
        listOf(R.string.nav_transactions, R.string.nav_calendar, R.string.nav_reports, R.string.nav_more).forEach { id ->
            compose.onAllNodesWithText(str(id)).onFirst().performClick()
            // The tab label plus the screen title.
            compose.onAllNodesWithText(str(id)).assertCountEquals(2)
        }
    }

    @Test fun addIncomeShowsOnHome() {
        val title = "நெல் விற்பனை ${System.nanoTime()}"

        compose.onNode(hasText(str(R.string.action_add_income)) and hasClickAction()).performClick()
        compose.onNode(hasText(str(R.string.field_amount)) and hasSetTextAction()).performTextInput("2500")
        compose.onNode(hasText(str(R.string.field_title)) and hasSetTextAction()).performTextInput(title)
        compose.onNodeWithText(str(R.string.cat_salary)).performScrollTo().performClick()
        compose.onNode(hasText(str(R.string.field_income_source)) and hasSetTextAction()).performScrollTo().performTextInput("Market")
        compose.onNodeWithText(ACCOUNT_NAME).performScrollTo().performClick()
        compose.onNodeWithText(str(R.string.action_save)).performClick()

        // The v2 test dispatcher queues the save coroutine, so wait for the result screen.
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithText(str(R.string.form_saved)).assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithText(str(R.string.action_done)).performClick()
        compose.onNodeWithText(title, substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun savingEmptyFormShowsErrorsAndStaysOpen() {
        compose.onNode(hasText(str(R.string.action_add_expense)) and hasClickAction()).performClick()
        compose.onNodeWithText(str(R.string.action_save)).performClick()
        // Amount and title are both empty, so the "required" message shows on each.
        compose.onAllNodesWithText(str(R.string.error_required), substring = true).assertCountEquals(2)
        compose.onNodeWithText(str(R.string.error_choose_category)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(str(R.string.form_fix_errors)).performScrollTo().assertIsDisplayed()
    }

    @Test fun languageSwitchesToEnglishAndBackWithoutChangingAmounts() {
        val container = (compose.activity.application as FamilyFinanceApplication).container
        runBlocking {
            container.transactionService.saveIncome(
                com.family.finance.domain.model.TransactionId("ui-lang-income"),
                com.family.finance.domain.validation.IncomeDraft(
                    "1234.50", "Lang test", com.family.finance.domain.model.DefaultCategories.idFor("salary"),
                    "Test", container.today(), AccountId("ui-test-cash"),
                ),
            )
        }
        fun openLanguageDialog() {
            compose.onAllNodesWithText(compose.activity.getString(R.string.nav_more)).onFirst().performClick()
            compose.onNodeWithText(compose.activity.getString(R.string.more_language)).performClick()
        }

        openLanguageDialog()
        compose.onNodeWithText("English").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Home").onFirst().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Balance").fetchSemanticsNodes().isNotEmpty() }
        val englishAmounts = compose.onAllNodesWithText("₹", substring = true).fetchSemanticsNodes().size

        openLanguageDialog()
        compose.onNodeWithText("தமிழ்").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("முகப்பு").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("முகப்பு").onFirst().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("மீதம்").fetchSemanticsNodes().isNotEmpty() }
        // Same rupee figures on screen in both languages (PRD 7.6).
        compose.onAllNodesWithText("₹", substring = true).assertCountEquals(englishAmounts)
    }

    private companion object {
        const val ACCOUNT_NAME = "UiTestCash"
    }
}
