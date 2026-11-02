package com.family.finance.domain.usecase

import com.family.finance.domain.calc.BalanceCalculator
import com.family.finance.domain.calc.DateRange
import com.family.finance.domain.calc.PeriodSummary
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.DefaultCategories
import com.family.finance.domain.model.Money
import com.family.finance.domain.model.Transaction
import com.family.finance.domain.model.TransactionId
import com.family.finance.domain.validation.ExpenseDraft
import com.family.finance.domain.validation.Field
import com.family.finance.domain.validation.IncomeDraft
import com.family.finance.domain.validation.Reason
import com.family.finance.domain.validation.TransferDraft
import com.family.finance.domain.validation.ValidationError
import com.family.finance.testing.MutableClock
import com.family.finance.testing.TODAY
import com.family.finance.testing.account
import com.family.finance.testing.testContainer
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class TransactionServiceTest {

    private val clock = MutableClock(TODAY.atTime(10, 0).toInstant(ZoneOffset.UTC))
    private val c = testContainer(clock)
    private val cash = AccountId("cash")
    private val bank = AccountId("bank")
    private val salary = DefaultCategories.idFor("salary")
    private val groceries = DefaultCategories.idFor("groceries")
    private val march = DateRange(TODAY.withDayOfMonth(1), TODAY.withDayOfMonth(31))

    private suspend fun setUpAccounts() {
        c.accountRepository.upsert(account("cash", opening = 50_000))
        c.accountRepository.upsert(account("bank", opening = 100_000))
    }

    private fun income(amount: String = "5000") =
        IncomeDraft(amount, "Paddy sale", salary, "Market", TODAY, cash, notes = "நெல் விற்பனை")

    private suspend fun all() = c.transactionRepository.observeAll().first()
    private suspend fun accounts() = c.accountRepository.observeAll().first()

    @Test fun `income and expense with no account, category or source go to cash and Other`() = runTest {
        c.accountRepository.upsert(account("bank", opening = 100_000).copy(type = com.family.finance.domain.model.AccountType.SAVINGS))
        c.accountRepository.upsert(account("cash", opening = 50_000).copy(createdAt = Instant.parse("2026-01-02T00:00:00Z")))
        c.accountRepository.upsert(account("cash_new").copy(createdAt = Instant.parse("2026-02-01T00:00:00Z")))
        c.accountRepository.upsert(account("cash_old_closed", active = false))

        assertThat(c.transactionService.saveIncome(TransactionId("i1"), IncomeDraft("5000", "Paddy sale", null, "", TODAY, null)))
            .isInstanceOf(SaveResult.Saved::class.java)
        assertThat(c.transactionService.saveExpense(TransactionId("e1"), ExpenseDraft("1200", "Urea", null, TODAY, null)))
            .isInstanceOf(SaveResult.Saved::class.java)

        val i1 = c.transactionRepository.get(TransactionId("i1")) as Transaction.Income
        assertThat(i1.accountId).isEqualTo(cash) // the oldest active cash account
        assertThat(i1.categoryId).isEqualTo(DefaultCategories.idFor("other_income"))
        assertThat(i1.source).isEmpty()
        val e1 = c.transactionRepository.get(TransactionId("e1")) as Transaction.Expense
        assertThat(e1.accountId).isEqualTo(cash)
        assertThat(e1.categoryId).isEqualTo(DefaultCategories.idFor("other_expenses"))

        // Balances stay derived from accounts: cash moved by +5000 − 1200, nothing else changed.
        val byId = accounts().associateBy { it.id }
        assertThat(BalanceCalculator.accountBalance(byId.getValue(cash), all())).isEqualTo(Money.ofRupees(50_000 + 5_000 - 1_200))
        assertThat(BalanceCalculator.accountBalance(byId.getValue(bank), all())).isEqualTo(Money.ofRupees(100_000))
    }

    @Test fun `an account picked by the person is kept, and with no cash account one must be picked`() = runTest {
        c.accountRepository.upsert(account("bank", opening = 100_000).copy(type = com.family.finance.domain.model.AccountType.SAVINGS))
        c.transactionService.saveIncome(TransactionId("i1"), IncomeDraft("5000", "Rent", salary, "", TODAY, bank))
        assertThat((c.transactionRepository.get(TransactionId("i1")) as Transaction.Income).accountId).isEqualTo(bank)

        val result = c.transactionService.saveExpense(TransactionId("e1"), ExpenseDraft("100", "Tea", groceries, TODAY, null))
        assertThat(result).isEqualTo(SaveResult.Invalid(listOf(ValidationError(Field.ACCOUNT, Reason.REQUIRED))))
    }

    @Test fun `saving income stores it with creator and timestamps`() = runTest {
        setUpAccounts()
        val id = TransactionId("t1")
        val result = c.transactionService.saveIncome(id, income())

        assertThat(result).isInstanceOf(SaveResult.Saved::class.java)
        val saved = c.transactionRepository.get(id) as Transaction.Income
        assertThat(saved.amount).isEqualTo(Money.ofRupees(5_000))
        assertThat(saved.notes).isEqualTo("நெல் விற்பனை")
        assertThat(saved.createdBy).isEqualTo(c.currentUser())
        assertThat(saved.createdAt).isEqualTo(clock.now)
    }

    @Test fun `saving the same id twice never duplicates (double tap or retry)`() = runTest {
        setUpAccounts()
        val id = TransactionId("t1")
        c.transactionService.saveIncome(id, income())
        c.transactionService.saveIncome(id, income())

        assertThat(all()).hasSize(1)
        assertThat(BalanceCalculator.accountBalance(accounts().first { it.id == cash }, all()))
            .isEqualTo(Money.ofRupees(55_000))
    }

    @Test fun `invalid draft stores nothing and returns the errors`() = runTest {
        setUpAccounts()
        val result = c.transactionService.saveIncome(TransactionId("t1"), income(amount = "0"))

        assertThat(result).isEqualTo(SaveResult.Invalid(listOf(ValidationError(Field.AMOUNT, Reason.NOT_POSITIVE))))
        assertThat(all()).isEmpty()
    }

    @Test fun `editing keeps the original creator and creation time`() = runTest {
        setUpAccounts()
        val id = TransactionId("t1")
        c.transactionService.saveIncome(id, income())
        val created = clock.now
        clock.now = Instant.parse("2026-03-20T08:00:00Z")

        c.transactionService.saveIncome(id, income(amount = "6000"))

        val edited = c.transactionRepository.get(id)!!
        assertThat(edited.createdAt).isEqualTo(created)
        assertThat(edited.updatedAt).isEqualTo(clock.now)
        assertThat(edited.amount).isEqualTo(Money.ofRupees(6_000))
    }

    @Test fun `editing a transaction updates the period report`() = runTest {
        setUpAccounts()
        val id = TransactionId("e1")
        c.transactionService.saveExpense(id, ExpenseDraft("1000", "Seeds", groceries, TODAY, cash))
        assertThat(PeriodSummary.calculate(accounts(), all(), march).totalExpense).isEqualTo(Money.ofRupees(1_000))

        c.transactionService.saveExpense(id, ExpenseDraft("1500", "Seeds", groceries, TODAY, cash))

        val summary = PeriodSummary.calculate(accounts(), all(), march)
        assertThat(summary.totalExpense).isEqualTo(Money.ofRupees(1_500))
        assertThat(summary.closingBalance).isEqualTo(Money.ofRupees(148_500))
    }

    @Test fun `deleting a transaction updates balances and the report`() = runTest {
        setUpAccounts()
        val id = TransactionId("e1")
        c.transactionService.saveExpense(id, ExpenseDraft("1000", "Seeds", groceries, TODAY, cash))
        c.transactionService.delete(id)

        val summary = PeriodSummary.calculate(accounts(), all(), march)
        assertThat(summary.totalExpense).isEqualTo(Money.ZERO)
        assertThat(BalanceCalculator.accountBalance(accounts().first { it.id == cash }, all()))
            .isEqualTo(Money.ofRupees(50_000))
    }

    @Test fun `editing a transfer moves both accounts together and keeps the combined balance`() = runTest {
        setUpAccounts()
        val id = TransactionId("tr1")
        c.transactionService.saveTransfer(id, TransferDraft("20000", cash, bank, TODAY))
        c.transactionService.saveTransfer(id, TransferDraft("5000", cash, bank, TODAY))

        val accts = accounts()
        assertThat(BalanceCalculator.accountBalance(accts.first { it.id == cash }, all())).isEqualTo(Money.ofRupees(45_000))
        assertThat(BalanceCalculator.accountBalance(accts.first { it.id == bank }, all())).isEqualTo(Money.ofRupees(105_000))
        assertThat(BalanceCalculator.combinedBalance(accts, all())).isEqualTo(Money.ofRupees(150_000))
    }

    @Test fun `deleting a transfer restores both accounts`() = runTest {
        setUpAccounts()
        val id = TransactionId("tr1")
        c.transactionService.saveTransfer(id, TransferDraft("20000", cash, bank, TODAY))
        c.transactionService.delete(id)

        val accts = accounts()
        assertThat(BalanceCalculator.accountBalance(accts.first { it.id == cash }, all())).isEqualTo(Money.ofRupees(50_000))
        assertThat(BalanceCalculator.accountBalance(accts.first { it.id == bank }, all())).isEqualTo(Money.ofRupees(100_000))
    }

    @Test fun `transfer description becomes its title and blank fields are stored as null`() = runTest {
        setUpAccounts()
        c.transactionService.saveTransfer(TransactionId("tr1"), TransferDraft("100", cash, bank, TODAY, "  "))
        c.transactionService.saveExpense(TransactionId("e1"), ExpenseDraft("10", "Tea", groceries, TODAY, cash, merchant = " "))

        val transfer = c.transactionRepository.get(TransactionId("tr1")) as Transaction.Transfer
        val expense = c.transactionRepository.get(TransactionId("e1")) as Transaction.Expense
        assertThat(transfer.title).isEmpty()
        assertThat(transfer.notes).isNull()
        assertThat(expense.merchant).isNull()
    }

    @Test fun `typed text is stored exactly as written, in any language, never translated`() = runTest {
        setUpAccounts()
        val title = "பால் விற்பனை – Milk sale (சுப்பிரமணி)"
        val source = "கூட்டுறவு பால் சங்கம்"
        val notes = "காலை 10 லிட்டர்,\nமாலை 8 லிட்டர் — Rate ₹42"
        c.transactionService.saveIncome(TransactionId("t1"), IncomeDraft("630", "  $title  ", salary, source, TODAY, cash, notes))

        val saved = c.transactionRepository.get(TransactionId("t1")) as Transaction.Income
        // Only surrounding spaces are trimmed; every character inside is kept, including line breaks.
        assertThat(saved.title).isEqualTo(title)
        assertThat(saved.source).isEqualTo(source)
        assertThat(saved.notes).isEqualTo(notes)
        assertThat(saved.title.codePoints().toArray()).isEqualTo(title.codePoints().toArray())
    }

    @Test fun `expense against an archived account is rejected`() = runTest {
        c.accountRepository.upsert(account("cash", active = false))
        val result = c.transactionService.saveExpense(TransactionId("e1"), ExpenseDraft("10", "Tea", groceries, TODAY, cash))
        assertThat(result).isEqualTo(SaveResult.Invalid(listOf(ValidationError(Field.ACCOUNT, Reason.INACTIVE))))
    }
}
