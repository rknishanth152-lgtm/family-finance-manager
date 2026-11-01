package com.family.finance

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
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.AccountType
import com.family.finance.domain.validation.AccountDraft
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

/**
 * Runs on a device or emulator. Strings come from the activity's resources, so the tests
 * pass in Tamil (the default) and in English.
 */
class AppFlowsTest {

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun str(id: Int) = rule.activity.getString(id)

    @Test fun bottomNavigationOpensEveryTab() {
        listOf(R.string.nav_transactions, R.string.nav_calendar, R.string.nav_reports, R.string.nav_more).forEach { id ->
            rule.onAllNodesWithText(str(id)).onFirst().performClick()
            // The tab label plus the screen title.
            rule.onAllNodesWithText(str(id)).assertCountEquals(2)
        }
    }

    @Test fun addIncomeShowsOnHome() {
        val container = (rule.activity.application as FamilyFinanceApplication).container
        runBlocking {
            container.accountService.save(
                AccountId("ui-test-cash"),
                AccountDraft("UiTestCash", AccountType.CASH, "100", container.today()),
            )
        }
        val title = "நெல் விற்பனை ${System.nanoTime()}"

        rule.onNode(hasText(str(R.string.action_add_income)) and hasClickAction()).performClick()
        rule.onNode(hasText(str(R.string.field_amount)) and hasSetTextAction()).performTextInput("2500")
        rule.onNode(hasText(str(R.string.field_title)) and hasSetTextAction()).performTextInput(title)
        rule.onNodeWithText(str(R.string.cat_salary)).performScrollTo().performClick()
        rule.onNode(hasText(str(R.string.field_income_source)) and hasSetTextAction()).performScrollTo().performTextInput("Market")
        rule.onNodeWithText("UiTestCash").performScrollTo().performClick()
        rule.onNodeWithText(str(R.string.action_save)).performClick()

        rule.onNodeWithText(str(R.string.form_saved)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.action_done)).performClick()
        rule.onNodeWithText(title, substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun savingEmptyFormShowsErrorsAndStaysOpen() {
        rule.onNode(hasText(str(R.string.action_add_expense)) and hasClickAction()).performClick()
        rule.onNodeWithText(str(R.string.action_save)).performClick()
        rule.onNodeWithText(str(R.string.error_required)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(str(R.string.form_fix_errors)).performScrollTo().assertIsDisplayed()
    }
}
