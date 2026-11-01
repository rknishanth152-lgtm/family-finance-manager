package com.family.finance

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.Locales
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.then
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import com.family.finance.data.AppContainer
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.AccountType
import com.family.finance.domain.model.DefaultCategories
import com.family.finance.domain.model.NoteId
import com.family.finance.domain.model.TransactionId
import com.family.finance.domain.validation.AccountDraft
import com.family.finance.domain.validation.ExpenseDraft
import com.family.finance.domain.validation.IncomeDraft
import com.family.finance.domain.validation.NoteDraft
import com.family.finance.domain.validation.TransferDraft
import com.family.finance.ui.common.LocalAppContainer
import com.family.finance.ui.navigation.FormKind
import com.family.finance.ui.navigation.Routes
import com.family.finance.ui.navigation.TopLevelDestination
import com.family.finance.ui.reports.EntriesFilter
import com.family.finance.domain.calc.DateRange
import java.time.LocalDate
import com.family.finance.ui.theme.FamilyFinanceTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Visits every screen in Tamil and English at 1x, 1.3x and 2x font size (PRD 7.3, 7.7,
 * 14.5) with deliberately long Tamil data. Fails if any text is clipped; text that is
 * deliberately shortened with "…" (long titles in lists) is allowed.
 *
 * Font size and language are overridden inside the test only; the phone's own settings
 * are never changed. Screenshots go to the app's files dir under screen-tour/ for review.
 */
class ScreenTourTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val outDir = File(instrumentation.targetContext.getExternalFilesDir(null), "screen-tour").apply {
        deleteRecursively()
        mkdirs()
    }

    private var language by mutableStateOf("ta")
    private var fontScale by mutableFloatStateOf(1f)
    private lateinit var nav: NavHostController
    private val problems = mutableListOf<String>()
    private var shotNumber = 0
    private lateinit var today: LocalDate
    private val firstOfMonth: LocalDate get() = today.withDayOfMonth(1)

    @Test fun everyScreenFitsInBothLanguagesAtLargeFonts() {
        val container = seededContainer()
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.FontScale(fontScale) then DeviceConfigurationOverride.Locales(LocaleList(language)),
            ) {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    FamilyFinanceTheme {
                        val controller = rememberNavController()
                        nav = controller
                        com.family.finance.ui.navigation.AppNavHost(controller)
                    }
                }
            }
        }

        for (lang in listOf("ta", "en")) {
            for (scale in listOf(1f, 1.3f, 2f)) {
                compose.runOnIdle {
                    language = lang
                    fontScale = scale
                }
                tour()
            }
        }
        assertTrue("Clipped text found:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    private fun tour() {
        top(TopLevelDestination.HOME); check("home")
        top(TopLevelDestination.TRANSACTIONS); check("transactions")
        clickDescription(R.string.action_filters); check("transactions-filters"); pressBack()
        top(TopLevelDestination.CALENDAR); check("calendar")
        top(TopLevelDestination.REPORTS); check("reports")
        scrollReportsTo(R.string.reports_section_expense_categories); check("reports-categories")
        scrollReportsTo(R.string.reports_section_accounts); check("reports-accounts")
        clickDescription(R.string.action_export); check("export-sheet"); pressBack()
        go(Routes.reportEntries(EntriesFilter(DateRange(firstOfMonth, today)))); check("report-entries")
        top(TopLevelDestination.MORE); check("more")
        clickText(R.string.more_language); check("language-dialog"); pressBack()
        top(TopLevelDestination.HOME); clickDescription(R.string.action_add_transaction); check("add-sheet"); pressBack()
        go(Routes.form(FormKind.INCOME)); check("form-income")
        go(Routes.form(FormKind.EXPENSE)); clickText(R.string.action_save); check("form-expense-errors")
        go(Routes.form(FormKind.TRANSFER)); check("form-transfer")
        go(Routes.detail(TransactionId(EXPENSE_ID))); check("detail")
        go(Routes.ACCOUNTS); check("accounts")
        go(Routes.accountEdit(AccountId("bank"))); check("account-edit")
        go(Routes.CATEGORIES); check("categories")
        go(Routes.NOTES); check("notebook")
        go(Routes.noteEdit(NoteId(NOTE_ID))); check("note-edit")
    }

    // --- navigation helpers ---

    /** Lazy lists only compose what is on screen, so scroll down to check the lower sections too. */
    private fun scrollReportsTo(headingRes: Int) {
        compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(hasText(str(headingRes)))
        compose.waitForIdle()
    }

    /** Closes the open dialog or sheet. */
    private fun pressBack() {
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
    }

    private fun top(destination: TopLevelDestination) {
        compose.runOnUiThread {
            nav.popBackStack(TopLevelDestination.HOME.route, inclusive = false)
            if (destination != TopLevelDestination.HOME) nav.navigate(destination.route)
        }
        compose.waitForIdle()
    }

    private fun go(route: String) {
        top(TopLevelDestination.HOME)
        compose.runOnUiThread { nav.navigate(route) }
        compose.waitForIdle()
    }

    private fun str(id: Int): String {
        val config = Configuration(instrumentation.targetContext.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        return instrumentation.targetContext.createConfigurationContext(config).getString(id)
    }

    private fun clickText(id: Int) {
        compose.onAllNodesWithText(str(id)).onFirst().performClick()
        compose.waitForIdle()
    }

    private fun clickDescription(id: Int) {
        compose.onAllNodes(hasContentDescription(str(id))).onFirst().performClick()
        compose.waitForIdle()
    }

    // --- checks ---

    private fun check(screen: String) {
        compose.waitForIdle()
        // Let navigation/sheet animations finish and the real frame reach the screen.
        Thread.sleep(SETTLE_MS)
        compose.waitForIdle()
        val label = "$screen [$language, ${fontScale}x]"
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
        compose.runOnUiThread {
            for (node in nodes) {
                if (SemanticsProperties.EditableText in node.config) continue // text fields scroll instead
                // Tabs sit in a sideways-scrolling row; their labels are never cut, just scrolled.
                if (generateSequence(node) { it.parent }.any { it.config.getOrNull(SemanticsProperties.Role) == Role.Tab }) continue
                val results = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                val layout = results.firstOrNull() ?: continue
                // "…" on purpose (long titles in lists) is fine; anything silently cut is not.
                if (layout.layoutInput.overflow == TextOverflow.Ellipsis) continue
                clipping(layout)?.let { problems += "$label: \"${layout.layoutInput.text.text}\" ($it)" }
            }
        }
        if (fontScale == 1f || fontScale == 2f) screenshot("$language-${fontScale}x-$screen")
    }

    /**
     * Measures real clipping against the text's own box. (TextLayoutResult.hasVisualOverflow
     * fires on almost every text here and is not usable for this.)
     */
    private fun clipping(layout: TextLayoutResult): String? {
        val tolerance = 1f
        val box = layout.size
        val widest = (0 until layout.lineCount).maxOfOrNull { layout.getLineRight(it) - layout.getLineLeft(it) } ?: 0f
        return when {
            layout.multiParagraph.didExceedMaxLines -> "cut after ${layout.lineCount} line(s)"
            widest > box.width + tolerance -> "too wide: ${widest.toInt()}px in ${box.width}px"
            layout.multiParagraph.height > box.height + tolerance -> "too tall: ${layout.multiParagraph.height.toInt()}px in ${box.height}px"
            else -> null
        }
    }

    private fun screenshot(name: String) {
        val full = instrumentation.uiAutomation.takeScreenshot() ?: return
        val small = Bitmap.createScaledBitmap(full, full.width / 2, full.height / 2, true)
        File(outDir, "%03d_%s.jpg".format(++shotNumber, name)).outputStream().use {
            small.compress(Bitmap.CompressFormat.JPEG, 80, it)
        }
    }

    // --- sample data: realistic, deliberately long Tamil, large amounts ---

    private fun seededContainer(): AppContainer {
        val c = AppContainer()
        today = c.today()
        val first = today.withDayOfMonth(1)
        runBlocking {
            c.accountService.save(AccountId("cash"), AccountDraft("ரொக்கம்", AccountType.CASH, "25000", first.minusMonths(2)))
            c.accountService.save(
                AccountId("bank"),
                AccountDraft("இந்தியன் வங்கி சேமிப்புக் கணக்கு", AccountType.SAVINGS, "196850.75", first.minusMonths(2)),
            )
            c.accountService.save(AccountId("kcc"), AccountDraft("Kisan Credit Card", AccountType.OTHER, "-50000", first.minusMonths(2)))
            val s = c.transactionService
            s.saveIncome(
                TransactionId("tour-income"),
                IncomeDraft(
                    "2190785.50", "நெல் விற்பனை – 40 மூட்டை (சம்பா)", DefaultCategories.idFor("agricultural_income"),
                    "திருவையாறு வேளாண் கூட்டுறவு சங்கம்", today, AccountId("bank"),
                ),
            )
            s.saveExpense(
                TransactionId(EXPENSE_ID),
                ExpenseDraft(
                    "2650", "உரம் – யூரியா 10 மூட்டை", DefaultCategories.idFor("fertilizers"), today, AccountId("cash"),
                    merchant = "ஸ்ரீ முருகன் உரக்கடை, திருவையாறு",
                    notes = "மூட்டைக்கு ₹265. அடுத்த வாரம் இன்னும் 5 மூட்டை தேவை.\nகடன் இல்லை – ரொக்கமாகச் செலுத்தப்பட்டது.",
                ),
            )
            s.saveExpense(
                TransactionId("tour-labour"),
                ExpenseDraft("4800", "விவசாயக் கூலி – நாற்று நடவு 6 பேர்", DefaultCategories.idFor("agri_labour"), first, AccountId("cash")),
            )
            s.saveExpense(
                TransactionId("tour-eb"),
                ExpenseDraft("1840.00", "Electricity bill – October", DefaultCategories.idFor("electricity"), first, AccountId("bank")),
            )
            s.saveTransfer(TransactionId("tour-atm"), TransferDraft("20000", AccountId("bank"), AccountId("cash"), today, "ATM எடுப்பு"))
            c.noteService.save(
                NoteId(NOTE_ID),
                NoteDraft("உர விலை – இந்த மாதம்", "யூரியா ₹265, டிஏபி ₹1,350, பொட்டாஷ் ₹1,700 (மூட்டைக்கு).", today),
            )
        }
        return c
    }

    private companion object {
        const val EXPENSE_ID = "tour-expense"
        const val NOTE_ID = "tour-note"
        const val SETTLE_MS = 600L
    }
}
