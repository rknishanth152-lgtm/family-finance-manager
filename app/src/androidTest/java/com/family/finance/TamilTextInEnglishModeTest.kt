package com.family.finance

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
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
 * With the app in English, text the user types in Tamil must be saved and shown exactly
 * as written, and be searchable (PRD 7.4, 7.5).
 */
class TamilTextInEnglishModeTest {

    private val setUp = object : ExternalResource() {
        override fun before() = pinLanguage("ta")
        override fun after() = pinLanguage("ta") // leave the phone in the app's default language
    }

    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(setUp).around(compose)

    private fun str(id: Int) = compose.activity.getString(id)

    @Test fun tamilTitleSourceAndNotesShowExactlyAsTyped() {
        val title = "பால் விற்பனை – சுப்பிரமணி ${System.nanoTime() % 10_000}"
        val source = "கூட்டுறவு பால் சங்கம்"
        val notes = "காலை 10 லிட்டர், மாலை 8 லிட்டர் (Rate ₹42)"

        // Switch the app to English the way a user does: More -> Language -> English.
        compose.onAllNodesWithText(str(R.string.nav_more)).onFirst().performClick()
        compose.onNodeWithText(str(R.string.more_language)).performClick()
        compose.onNodeWithText("English").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Home").onFirst().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(str(R.string.action_add_income)).fetchSemanticsNodes().isNotEmpty() }

        compose.onNode(hasText(str(R.string.action_add_income)) and hasClickAction()).performClick()
        compose.onNode(hasText(str(R.string.field_amount)) and hasSetTextAction()).performTextInput("1500")
        compose.onNode(hasText(str(R.string.field_title)) and hasSetTextAction()).performTextInput(title)
        compose.onNodeWithText(str(R.string.cat_salary)).performScrollTo().performClick()
        compose.onNode(hasText(str(R.string.field_income_source)) and hasSetTextAction()).performScrollTo().performTextInput(source)
        compose.onNodeWithText(ACCOUNT_NAME).performScrollTo().performClick()
        compose.onNodeWithText(str(R.string.action_more_options)).performScrollTo().performClick()
        compose.onNode(hasText(str(R.string.field_notes)) and hasSetTextAction()).performScrollTo().performTextInput(notes)
        compose.onNodeWithText(str(R.string.action_save)).performClick()

        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(str(R.string.action_view_transaction)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(str(R.string.action_view_transaction)).performClick()

        // Detail screen: every field exactly as typed, English labels around it.
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText(source).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(notes).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Received from").performScrollTo().assertIsDisplayed()

        // Search in English mode with a Tamil word finds it.
        compose.onNodeWithContentDescription(str(R.string.action_back)).performClick()
        compose.onAllNodesWithText("Transactions").onFirst().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("சுப்பிரமணி")
        compose.waitUntil(5_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(title).assertIsDisplayed()
    }

    private companion object {
        const val ACCOUNT_NAME = "UiTestCash"

        fun pinLanguage(tag: String) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            // Set both Android's per-app language and AppCompat's own copy, so a language
            // switched by an earlier test (through AppCompat) cannot win over this one.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
            }
            instrumentation.runOnMainSync {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
            }
            val container = TestSession.install()
            runBlocking {
                container.accountService.save(
                    AccountId("ui-test-cash"),
                    AccountDraft(ACCOUNT_NAME, AccountType.CASH, "100", container.today()),
                )
            }
        }
    }
}
