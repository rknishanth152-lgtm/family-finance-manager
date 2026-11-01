package com.family.finance

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.family.finance.data.AppContainer
import com.family.finance.data.export.ExportFormat
import com.family.finance.data.export.ExportRequest
import com.family.finance.data.export.ExportService
import com.family.finance.domain.calc.DateRange
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.AccountType
import com.family.finance.domain.model.DefaultCategories
import com.family.finance.domain.model.TransactionId
import com.family.finance.domain.model.UserId
import com.family.finance.domain.report.ReportFilter
import com.family.finance.domain.report.Reports
import com.family.finance.domain.validation.AccountDraft
import com.family.finance.domain.validation.ExpenseDraft
import com.family.finance.domain.validation.IncomeDraft
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Real export files on a real phone (PRD 12.1: "test actual exported files"). Each file
 * is made in Tamil and in English, whatever the app's own language is, and the PDF pages
 * are rendered to images under files/export-check/ for a visual check.
 */
class ExportOnDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val service = ExportService(context)
    private val outDir = File(context.getExternalFilesDir(null), "export-check").apply { deleteRecursively(); mkdirs() }

    private val title = "பால் விற்பனை – சுப்பிரமணி"
    private val notes = "காலை 10 லிட்டர், மாலை 8 லிட்டர் (Rate ₹42) க்ஷ ஸ்ரீ"

    private fun request(format: ExportFormat, language: AppLanguage): ExportRequest = runBlocking {
        val c = AppContainer()
        val today = c.today()
        val first = today.withDayOfMonth(1)
        c.accountService.save(AccountId("cash"), AccountDraft("ரொக்கம்", AccountType.CASH, "5000", first.minusMonths(1)))
        c.accountService.save(AccountId("bank"), AccountDraft("இந்தியன் வங்கி", AccountType.SAVINGS, "196850.75", first.minusMonths(1)))
        c.transactionService.saveIncome(
            TransactionId("i1"),
            IncomeDraft("2190785.50", title, DefaultCategories.idFor("agricultural_income"), "கூட்டுறவு சங்கம்", first.minusMonths(1).plusDays(4), AccountId("bank"), notes = notes),
        )
        c.transactionService.saveExpense(
            TransactionId("e1"),
            ExpenseDraft("2650", "உரம் – யூரியா 10 மூட்டை", DefaultCategories.idFor("fertilizers"), today, AccountId("cash"), merchant = "=1+1"),
        )
        // Enough entries over three months for a chart and a second page.
        repeat(40) { i ->
            c.transactionService.saveExpense(
                TransactionId("many-$i"),
                ExpenseDraft("${100 + i * 37}.25", "கூலி – நாள் ${i + 1}", DefaultCategories.idFor("agri_labour"), first.minusDays(1L + i), AccountId("cash")),
            )
        }
        val accounts = c.accountRepository.observeAll().first()
        val categories = c.categoryRepository.observeAll().first()
        val txns = c.transactionRepository.observeAll().first()
        val report = Reports.build(ReportFilter(DateRange(first.minusMonths(2), today)), txns, accounts, categories)
        ExportRequest(
            report, format, language, familyName = "எங்கள் குடும்பம்", today = today,
            accounts = accounts.associateBy { it.id }, categories = categories.associateBy { it.id },
            people = mapOf(UserId("local-user") to "அப்பா"),
        )
    }

    @Test fun pdfInBothLanguagesEmbedsTheTamilFontAndRenders() = runBlocking<Unit> {
        for (language in AppLanguage.entries) {
            val file = service.export(request(ExportFormat.PDF, language)).file
            val bytes = file.readBytes()
            assertThat(bytes.copyOfRange(0, 5).toString(Charsets.US_ASCII)).isEqualTo("%PDF-")
            val raw = bytes.toString(Charsets.ISO_8859_1)
            assertThat(raw).contains("FontFile2")
            assertThat(raw).contains("NotoSansTamil")

            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { pdf ->
                    assertThat(pdf.pageCount).isAtLeast(1)
                    for (i in 0 until pdf.pageCount) {
                        pdf.openPage(i).use { page ->
                            val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            File(outDir, "pdf-${language.tag}-page${i + 1}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        }
                    }
                }
            }
            file.copyTo(File(outDir, "statement-${language.tag}.pdf"), overwrite = true)
        }
    }

    @Test fun csvLabelsFollowTheChosenLanguageAndTypedTamilIsExact() = runBlocking<Unit> {
        val ta = service.export(request(ExportFormat.CSV, AppLanguage.TAMIL)).file.readText()
        val en = service.export(request(ExportFormat.CSV, AppLanguage.ENGLISH)).file.readText()
        assertThat(ta.removePrefix("\uFEFF")).startsWith("தேதி,")
        assertThat(en.removePrefix("\uFEFF")).startsWith("Date,")
        for (csv in listOf(ta, en)) {
            assertThat(csv).contains(title)
            assertThat(csv).contains(notes)
            assertThat(csv).contains("2190785.50")
            assertThat(csv).contains("'=1+1")
        }
        assertThat(en).contains("Agricultural income")
    }

    @Test fun excelSheetsAreNamedInTheChosenLanguage() = runBlocking<Unit> {
        fun workbook(language: AppLanguage): String {
            val file = runBlocking { service.export(request(ExportFormat.XLSX, language)).file }
            ZipInputStream(file.inputStream()).use { zip ->
                generateSequence { zip.nextEntry }.forEach { if (it.name == "xl/workbook.xml") return zip.readBytes().toString(Charsets.UTF_8) }
            }
            error("workbook.xml missing")
        }
        assertThat(workbook(AppLanguage.TAMIL)).contains("சுருக்கம்")
        assertThat(workbook(AppLanguage.ENGLISH)).contains("Summary")
    }

    @Test fun exportsCanBeSharedAndSavedAndOnlyTheLatestIsKept() = runBlocking<Unit> {
        val first = service.export(request(ExportFormat.CSV, AppLanguage.TAMIL))
        val second = service.export(request(ExportFormat.XLSX, AppLanguage.ENGLISH))
        assertThat(first.file.exists()).isFalse()
        assertThat(second.file.parentFile!!.listFiles()!!.map { it.name }).containsExactly(second.displayName)

        // The share link resolves through the app's own file provider.
        val shareUri = FileProvider.getUriForFile(context, "${context.packageName}.exports", second.file)
        assertThat(shareUri.authority).isEqualTo("${context.packageName}.exports")

        // Saving copies the file byte for byte to the chosen place.
        val target = File(outDir, "saved.xlsx")
        service.saveTo(second.file, Uri.fromFile(target))
        assertThat(target.readBytes()).isEqualTo(second.file.readBytes())
    }
}
