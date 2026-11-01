package com.family.finance.domain.usecase

import com.family.finance.domain.calc.BalanceCalculator
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.AccountType
import com.family.finance.domain.model.CategoryId
import com.family.finance.domain.model.CategoryKind
import com.family.finance.domain.model.DefaultCategories
import com.family.finance.domain.model.Money
import com.family.finance.domain.model.NoteId
import com.family.finance.domain.model.TransactionId
import com.family.finance.domain.validation.AccountDraft
import com.family.finance.domain.validation.ExpenseDraft
import com.family.finance.domain.validation.Field
import com.family.finance.domain.validation.NoteDraft
import com.family.finance.domain.validation.Reason
import com.family.finance.domain.validation.ValidationError
import com.family.finance.testing.TODAY
import com.family.finance.testing.testContainer
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RecordServicesTest {

    private val c = testContainer()

    @Test fun `account is saved with parsed opening balance, blank meaning zero`() = runTest {
        val r1 = c.accountService.save(AccountId("a"), AccountDraft("Cash", AccountType.CASH, "1,500.50", TODAY))
        val r2 = c.accountService.save(AccountId("b"), AccountDraft("Bank", AccountType.SAVINGS, "", TODAY))
        assertThat((r1 as RecordResult.Saved).value.openingBalance).isEqualTo(Money(150_050))
        assertThat((r2 as RecordResult.Saved).value.openingBalance).isEqualTo(Money.ZERO)
    }

    @Test fun `account validation reports name, balance format and date`() = runTest {
        val r = c.accountService.save(AccountId("a"), AccountDraft(" ", AccountType.CASH, "12.345", null))
        assertThat((r as RecordResult.Invalid).errors).containsExactly(
            ValidationError(Field.NAME, Reason.REQUIRED),
            ValidationError(Field.OPENING_BALANCE, Reason.INVALID_FORMAT),
            ValidationError(Field.DATE, Reason.REQUIRED),
        )
        assertThat(c.accountRepository.observeAll().first()).isEmpty()
    }

    @Test fun `archiving an account keeps its history and its money in the family total`() = runTest {
        val cash = AccountId("cash")
        c.accountService.save(cash, AccountDraft("Cash", AccountType.CASH, "1000", TODAY.minusDays(30)))
        c.transactionService.saveExpense(
            TransactionId("e1"),
            ExpenseDraft("200", "Tea", DefaultCategories.idFor("food"), TODAY, cash),
        )
        c.accountService.setActive(cash, false)

        val accounts = c.accountRepository.observeAll().first()
        val txns = c.transactionRepository.observeAll().first()
        assertThat(accounts.single().isActive).isFalse()
        assertThat(txns).hasSize(1)
        assertThat(BalanceCalculator.combinedBalance(accounts, txns)).isEqualTo(Money.ofRupees(800))
    }

    @Test fun `renaming a category keeps its id so history follows`() = runTest {
        val seeds = DefaultCategories.idFor("seeds")
        c.categoryService.rename(seeds, "நெல் விதை")
        val renamed = c.categoryRepository.get(seeds)!!
        assertThat(renamed.id).isEqualTo(seeds)
        assertThat(renamed.name).isEqualTo("நெல் விதை")
        assertThat(renamed.systemKey).isEqualTo("seeds")
    }

    @Test fun `new category needs a name and archiving keeps it resolvable`() = runTest {
        val blank = c.categoryService.create(CategoryId("x"), "  ", CategoryKind.EXPENSE)
        assertThat(blank).isEqualTo(RecordResult.Invalid(listOf(ValidationError(Field.NAME, Reason.REQUIRED))))

        c.categoryService.create(CategoryId("x"), "Tractor diesel", CategoryKind.EXPENSE)
        c.categoryService.setArchived(CategoryId("x"), true)
        assertThat(c.categoryRepository.get(CategoryId("x"))!!.isArchived).isTrue()
    }

    @Test fun `notes save, edit, delete and never touch balances`() = runTest {
        val id = NoteId("n1")
        c.noteService.save(id, NoteDraft("உரம் விலை", "Urea ₹300 per bag", TODAY))
        c.noteService.save(id, NoteDraft("உரம் விலை", "Urea ₹320 per bag", TODAY))
        assertThat(c.noteRepository.observeAll().first().single().description).isEqualTo("Urea ₹320 per bag")
        assertThat(c.transactionRepository.observeAll().first()).isEmpty()

        c.noteService.delete(id)
        assertThat(c.noteRepository.observeAll().first()).isEmpty()
    }

    @Test fun `note requires a title`() = runTest {
        val r = c.noteService.save(NoteId("n1"), NoteDraft("", "x", TODAY))
        assertThat(r).isEqualTo(RecordResult.Invalid(listOf(ValidationError(Field.TITLE, Reason.REQUIRED))))
    }

    @Test fun `default categories have unique stable ids and mark loans`() {
        val ids = DefaultCategories.all.map { it.id }
        assertThat(ids).containsNoDuplicates()
        assertThat(DefaultCategories.all.filter { it.isLoan }.map { it.systemKey })
            .containsExactly("loan_received", "loan_principal")
        assertThat(DefaultCategories.idFor("salary")).isEqualTo(CategoryId("sys.salary"))
    }
}
