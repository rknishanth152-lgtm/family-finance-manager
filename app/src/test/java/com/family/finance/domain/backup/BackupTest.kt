package com.family.finance.domain.backup

import com.family.finance.data.AppContainer
import com.family.finance.domain.calc.BalanceCalculator
import com.family.finance.domain.calc.IncomeExpenseTotals
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.AccountType
import com.family.finance.domain.model.CategoryId
import com.family.finance.domain.model.CategoryKind
import com.family.finance.domain.model.DefaultCategories
import com.family.finance.domain.model.Money
import com.family.finance.domain.model.NoteId
import com.family.finance.domain.model.Transaction
import com.family.finance.domain.model.TransactionId
import com.family.finance.domain.model.UserId
import com.family.finance.domain.validation.AccountDraft
import com.family.finance.domain.validation.ExpenseDraft
import com.family.finance.domain.validation.IncomeDraft
import com.family.finance.domain.validation.NoteDraft
import com.family.finance.domain.validation.TransferDraft
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class BackupTest {

    private val clock = Clock.fixed(Instant.parse("2026-11-01T06:30:00Z"), ZoneOffset.UTC)
    private val day = LocalDate.parse("2026-10-15")
    private val tamilTitle = "நெல் விற்பனை – 40 மூட்டை \"சம்பா\"\nஇரண்டாம் வரி"

    private fun family(user: String = "father") = AppContainer(clock = clock, currentUser = { UserId(user) })

    /** A family with every kind of record, including an archived account and a custom Tamil category. */
    private suspend fun seeded(): AppContainer = family().apply {
        accountService.save(AccountId("cash"), AccountDraft("ரொக்கம்", AccountType.CASH, "25000.50", day.minusMonths(1)))
        accountService.save(AccountId("bank"), AccountDraft("Indian Bank", AccountType.SAVINGS, "196850.75", day.minusMonths(1)))
        val milk = CategoryId("milk")
        categoryService.create(milk, "பால் விற்பனை", CategoryKind.INCOME)
        transactionService.saveIncome(TransactionId("i1"), IncomeDraft("2190785.50", tamilTitle, milk, "கூட்டுறவு சங்கம்", day, AccountId("bank"), notes = "குறிப்பு"))
        transactionService.saveExpense(TransactionId("e1"), ExpenseDraft("2650.05", "உரம்", DefaultCategories.idFor("fertilizers"), day, AccountId("cash"), merchant = "=1+1"))
        transactionService.saveTransfer(TransactionId("t1"), TransferDraft("20000", AccountId("bank"), AccountId("cash"), day, "ATM"))
        noteService.save(NoteId("n1"), NoteDraft("உர விலை", "யூரியா ₹265", day))
        accountService.setActive(AccountId("cash"), false)
    }

    private suspend fun roundTrip(c: AppContainer): Backup {
        val text = BackupCodec.encode(c.backupService.create("எங்கள் குடும்பம்"))
        return (BackupCodec.decode(text) as BackupRead.Ok).backup
    }

    @Test fun `a backup reads back exactly, Tamil text and paise included`() = runTest {
        val c = seeded()
        val original = c.backupService.create("எங்கள் குடும்பம்")
        val read = BackupCodec.decode(BackupCodec.encode(original))
        assertThat(read).isEqualTo(BackupRead.Ok(original))
        val b = (read as BackupRead.Ok).backup
        assertThat(b.transactions.single { it.id.value == "i1" }.title).isEqualTo(tamilTitle)
        assertThat(b.transactions.single { it.id.value == "e1" }.amount).isEqualTo(Money(265_005))
        assertThat(b.accounts.single { it.id.value == "cash" }.isActive).isFalse()
    }

    @Test fun `restoring into an empty family gives the same totals and balances (PRD 15_4)`() = runTest {
        val source = seeded()
        val backup = roundTrip(source)
        val target = family("mother")
        val plan = target.backupService.plan(backup)
        assertThat(plan.canRestore).isTrue()
        target.backupService.restore(plan)

        val before = source.transactionRepository.observeAll().first()
        val after = target.transactionRepository.observeAll().first()
        assertThat(IncomeExpenseTotals.of(after)).isEqualTo(IncomeExpenseTotals.of(before))
        val accountsBefore = source.accountRepository.observeAll().first()
        val accountsAfter = target.accountRepository.observeAll().first()
        assertThat(BalanceCalculator.combinedBalance(accountsAfter, after)).isEqualTo(BalanceCalculator.combinedBalance(accountsBefore, before))
        for (a in accountsBefore) {
            assertThat(BalanceCalculator.accountBalance(accountsAfter.single { it.id == a.id }, after))
                .isEqualTo(BalanceCalculator.accountBalance(a, before))
        }
        assertThat(target.noteRepository.observeAll().first().single().title).isEqualTo("உர விலை")
        assertThat(target.categoryRepository.observeAll().first().map { it.name }).contains("பால் விற்பனை")
    }

    @Test fun `restored records are created by the person restoring, with dates kept`() = runTest {
        val backup = roundTrip(seeded())
        val target = family("mother")
        target.backupService.restore(target.backupService.plan(backup))
        val restored = target.transactionRepository.observeAll().first()
        assertThat(restored.map { it.createdBy }.toSet()).containsExactly(UserId("mother"))
        assertThat(restored.single { it.id.value == "i1" }.date).isEqualTo(day)
    }

    @Test fun `restore only adds what is missing and never changes existing records`() = runTest {
        val c = seeded()
        val backup = roundTrip(c)
        // After the backup: one entry edited, one deleted.
        c.transactionService.saveExpense(TransactionId("e1"), ExpenseDraft("9999", "மாற்றப்பட்டது", DefaultCategories.idFor("fertilizers"), day, AccountId("bank")))
        c.transactionService.delete(TransactionId("t1"))

        val plan = c.backupService.plan(backup)
        // A deletion made after the backup is respected, not undone.
        assertThat(plan.toAdd.transactions).isEmpty()
        assertThat(plan.deletedHere).isEqualTo(1)
        assertThat(plan.differ).isEqualTo(1)
        val result = c.backupService.restore(plan)
        assertThat(result.transactions).isEqualTo(0)

        val e1 = c.transactionRepository.get(TransactionId("e1"))!!
        assertThat(e1.title).isEqualTo("மாற்றப்பட்டது")
        assertThat(e1.amount).isEqualTo(Money.ofRupees(9999))
        assertThat(c.transactionRepository.get(TransactionId("t1"))).isNull()

        // A record that is truly missing (never here) is still added.
        val fresh = family("mother")
        assertThat(fresh.backupService.plan(backup).toAdd.transactions.map { it.id.value }).contains("t1")
    }

    @Test fun `restoring the same backup twice adds nothing the second time`() = runTest {
        val backup = roundTrip(seeded())
        val target = family("mother")
        target.backupService.restore(target.backupService.plan(backup))
        val again = target.backupService.plan(backup)
        assertThat(again.addCount).isEqualTo(0)
        assertThat(target.backupService.restore(again).total).isEqualTo(0)
        assertThat(target.transactionRepository.observeAll().first()).hasSize(3)
    }

    @Test fun `entries pointing at a missing account block the restore`() = runTest {
        val backup = roundTrip(seeded())
        val broken = backup.copy(accounts = backup.accounts.filter { it.id.value != "cash" })
        val plan = family("mother").backupService.plan(broken)
        assertThat(plan.canRestore).isFalse()
        assertThat(plan.brokenLinks.joinToString()).contains("cash")
    }

    @Test fun `files that are not backups, damaged, edited or newer are rejected`() = runTest {
        val good = BackupCodec.encode(seeded().backupService.create("F"))
        fun problem(text: String) = (BackupCodec.decode(text) as BackupRead.Invalid).problem

        assertThat(problem("not json at all")).isEqualTo(BackupProblem.UNREADABLE)
        assertThat(problem(good.dropLast(40))).isEqualTo(BackupProblem.UNREADABLE)
        assertThat(problem("""{"hello":1}""")).isEqualTo(BackupProblem.NOT_A_BACKUP)
        assertThat(problem(good.replace("\"formatVersion\":1", "\"formatVersion\":2"))).isEqualTo(BackupProblem.NEWER_VERSION)
        // An amount changed by hand no longer matches the totals written in the file.
        assertThat(problem(good.replace("\"amountPaise\":265005", "\"amountPaise\":265006"))).isEqualTo(BackupProblem.TOTALS_MISMATCH)
        // A decimal amount is never accepted as money.
        assertThat(problem(good.replace("\"amountPaise\":265005", "\"amountPaise\":2650.05"))).isEqualTo(BackupProblem.UNREADABLE)
        assertThat(problem(good.replace("\"date\":\"2026-10-15\"", "\"date\":\"15/10/2026\""))).isEqualTo(BackupProblem.BAD_RECORD)
        assertThat(problem(good.replace("\"amountPaise\":265005", "\"amountPaise\":0"))).isEqualTo(BackupProblem.BAD_RECORD)
        assertThat(problem(good.replace("\"id\":\"t1\"", "\"id\":\"e1\""))).isEqualTo(BackupProblem.DUPLICATE_ID)
    }

    @Test fun `edited files with unsafe IDs, dates, titles or amounts are rejected`() = runTest {
        val good = BackupCodec.encode(seeded().backupService.create("F"))
        fun problem(text: String) = (BackupCodec.decode(text) as BackupRead.Invalid).problem

        // IDs become cloud document names: no paths, no reserved names, no empties.
        for (bad in listOf("a/b", "..", ".", "__x__", "", "x".repeat(201))) {
            assertThat(problem(good.replace("\"id\":\"n1\"", "\"id\":\"$bad\""))).isEqualTo(BackupProblem.BAD_RECORD)
        }
        assertThat(problem(good.replace("\"date\":\"2026-10-15\"", "\"date\":\"0000-10-15\""))).isEqualTo(BackupProblem.BAD_RECORD)
        assertThat(problem(good.replace("\"title\":\"உர விலை\"", "\"title\":\"  \""))).isEqualTo(BackupProblem.BAD_RECORD)
        assertThat(problem(good.replace("\"amountPaise\":265005", "\"amountPaise\":${Money.MAX_AMOUNT_PAISE + 1}"))).isEqualTo(BackupProblem.BAD_RECORD)
        assertThat(problem(good.replace("\"openingBalancePaise\":2500050", "\"openingBalancePaise\":-${Money.MAX_AMOUNT_PAISE + 1}"))).isEqualTo(BackupProblem.BAD_RECORD)
    }

    @Test fun `restore leaves records deleted here since the backup deleted`() = runTest {
        val c = seeded()
        val backup = roundTrip(c)
        c.transactionService.delete(TransactionId("e1"))
        c.noteService.delete(NoteId("n1"))

        val plan = c.backupService.plan(backup)
        assertThat(plan.deletedHere).isEqualTo(2)
        assertThat(plan.addCount).isEqualTo(0)
        c.backupService.restore(plan)
        assertThat(c.transactionRepository.get(TransactionId("e1"))).isNull()
        assertThat(c.noteRepository.get(NoteId("n1"))).isNull()
    }

    @Test fun `a file with a byte order mark still reads`() = runTest {
        val good = BackupCodec.encode(seeded().backupService.create("F"))
        assertThat(BackupCodec.decode("\uFEFF" + good)).isInstanceOf(BackupRead.Ok::class.java)
    }

    @Test fun `transfers keep both accounts`() = runTest {
        val t = roundTrip(seeded()).transactions.single { it.id.value == "t1" } as Transaction.Transfer
        assertThat(t.fromAccountId).isEqualTo(AccountId("bank"))
        assertThat(t.toAccountId).isEqualTo(AccountId("cash"))
    }
}
