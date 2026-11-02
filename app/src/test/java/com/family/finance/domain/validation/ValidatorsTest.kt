package com.family.finance.domain.validation

import com.family.finance.domain.Fixtures.BANK_ID
import com.family.finance.domain.Fixtures.CASH_ID
import com.family.finance.domain.Fixtures.GROCERIES_ID
import com.family.finance.domain.Fixtures.SALARY_ID
import com.family.finance.domain.Fixtures.account
import com.family.finance.domain.Fixtures.category
import com.family.finance.domain.Fixtures.date
import com.family.finance.domain.Fixtures.rupees
import com.family.finance.domain.Fixtures.transfer
import com.family.finance.domain.model.AccountId
import com.family.finance.domain.model.CategoryId
import com.family.finance.domain.model.CategoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsTest {

    private val closed = AccountId("closed")
    private val oldCategory = CategoryId("old")
    private val accounts = listOf(
        account(CASH_ID), account(BANK_ID), account(closed, active = false),
    ).associateBy { it.id }
    private val categories = listOf(
        category(SALARY_ID, CategoryKind.INCOME),
        category(GROCERIES_ID, CategoryKind.EXPENSE),
        category(oldCategory, CategoryKind.EXPENSE, archived = true),
    ).associateBy { it.id }

    private val validIncome = IncomeDraft(
        amountText = "15000", title = "சம்பளம்", categoryId = SALARY_ID,
        source = "Employer", date = date("2026-03-01"), accountId = BANK_ID,
    )
    private val validExpense = ExpenseDraft(
        amountText = "250.75", title = "காய்கறி", categoryId = GROCERIES_ID,
        date = date("2026-03-01"), accountId = CASH_ID,
    )
    private val validTransfer = TransferDraft(
        amountText = "20000", fromAccountId = CASH_ID, toAccountId = BANK_ID, date = date("2026-03-01"),
    )

    private fun income(d: IncomeDraft) = IncomeValidator.validate(d, accounts, categories)
    private fun expense(d: ExpenseDraft) = ExpenseValidator.validate(d, accounts, categories)
    private fun transfer(d: TransferDraft) = TransferValidator.validate(d, accounts)
    private fun err(field: Field, reason: Reason) = listOf(ValidationError(field, reason))

    // --- amounts: zero, negative, rounding ---

    @Test fun `valid drafts have no errors, including Tamil titles`() {
        assertTrue(income(validIncome).isEmpty())
        assertTrue(expense(validExpense).isEmpty())
        assertTrue(transfer(validTransfer).isEmpty())
    }

    @Test fun `amounts above Rs 10,000 crore are rejected, so totals can never overflow`() {
        val e = err(Field.AMOUNT, Reason.TOO_LARGE)
        assertEquals(e, income(validIncome.copy(amountText = "100000000000.01")))
        assertEquals(e, expense(validExpense.copy(amountText = "100000000001")))
        assertEquals(e, transfer(validTransfer.copy(amountText = "100000000001")))
        assertTrue(expense(validExpense.copy(amountText = "100000000000")).isEmpty())
        val account = com.family.finance.domain.validation.AccountDraft("Bank", com.family.finance.domain.model.AccountType.SAVINGS, "-100000000001", date("2026-03-01"))
        assertEquals(err(Field.OPENING_BALANCE, Reason.TOO_LARGE), AccountValidator.validate(account))
        assertEquals(err(Field.OPENING_BALANCE, Reason.INVALID_FORMAT), AccountValidator.validate(account.copy(openingBalanceText = "12abc")))
    }

    @Test fun `zero amount is rejected for every type`() {
        val e = err(Field.AMOUNT, Reason.NOT_POSITIVE)
        assertEquals(e, income(validIncome.copy(amountText = "0")))
        assertEquals(e, expense(validExpense.copy(amountText = "0.00")))
        assertEquals(e, transfer(validTransfer.copy(amountText = "0")))
    }

    @Test fun `negative amount is rejected for every type`() {
        val e = err(Field.AMOUNT, Reason.NOT_POSITIVE)
        assertEquals(e, income(validIncome.copy(amountText = "-100")))
        assertEquals(e, expense(validExpense.copy(amountText = "-0.01")))
        assertEquals(e, transfer(validTransfer.copy(amountText = "-20000")))
    }

    @Test fun `amount with more than two decimals is rejected, not rounded`() {
        val e = err(Field.AMOUNT, Reason.INVALID_FORMAT)
        assertEquals(e, income(validIncome.copy(amountText = "100.005")))
        assertEquals(e, expense(validExpense.copy(amountText = "0.001")))
        assertEquals(e, transfer(validTransfer.copy(amountText = "1.999")))
    }

    @Test fun `smallest positive amount of one paisa is accepted`() {
        assertTrue(expense(validExpense.copy(amountText = "0.01")).isEmpty())
    }

    @Test fun `blank and malformed amounts are rejected`() {
        assertEquals(err(Field.AMOUNT, Reason.REQUIRED), income(validIncome.copy(amountText = "  ")))
        assertEquals(err(Field.AMOUNT, Reason.INVALID_FORMAT), expense(validExpense.copy(amountText = "abc")))
    }

    // --- income (PRD 6.2) ---

    @Test fun `income requires title, date and account, but not who it was received from`() {
        assertTrue(income(validIncome.copy(source = "  ")).isEmpty())
        val errors = income(validIncome.copy(title = " ", source = "", date = null, accountId = null))
        assertEquals(
            setOf(
                ValidationError(Field.TITLE, Reason.REQUIRED),
                ValidationError(Field.DATE, Reason.REQUIRED),
                ValidationError(Field.ACCOUNT, Reason.REQUIRED),
            ),
            errors.toSet(),
        )
    }

    @Test fun `income category must exist and be an income category`() {
        assertEquals(err(Field.CATEGORY, Reason.REQUIRED), income(validIncome.copy(categoryId = null)))
        assertEquals(err(Field.CATEGORY, Reason.NOT_FOUND), income(validIncome.copy(categoryId = CategoryId("x"))))
        assertEquals(err(Field.CATEGORY, Reason.WRONG_KIND), income(validIncome.copy(categoryId = GROCERIES_ID)))
    }

    @Test fun `income destination account must exist and be active`() {
        assertEquals(err(Field.ACCOUNT, Reason.NOT_FOUND), income(validIncome.copy(accountId = AccountId("x"))))
        assertEquals(err(Field.ACCOUNT, Reason.INACTIVE), income(validIncome.copy(accountId = closed)))
    }

    // --- expense (PRD 6.3) ---

    @Test fun `expense requires title and date`() {
        assertEquals(
            setOf(ValidationError(Field.TITLE, Reason.REQUIRED), ValidationError(Field.DATE, Reason.REQUIRED)),
            expense(validExpense.copy(title = "", date = null)).toSet(),
        )
    }

    @Test fun `expense category must be an active expense category`() {
        assertEquals(err(Field.CATEGORY, Reason.WRONG_KIND), expense(validExpense.copy(categoryId = SALARY_ID)))
        assertEquals(err(Field.CATEGORY, Reason.ARCHIVED), expense(validExpense.copy(categoryId = oldCategory)))
    }

    @Test fun `expense source account must be valid`() {
        assertEquals(err(Field.ACCOUNT, Reason.REQUIRED), expense(validExpense.copy(accountId = null)))
        assertEquals(err(Field.ACCOUNT, Reason.INACTIVE), expense(validExpense.copy(accountId = closed)))
    }

    // --- transfer (PRD 6.4) ---

    @Test fun `transfer with the same source and destination is rejected`() {
        assertEquals(
            err(Field.TO_ACCOUNT, Reason.SAME_AS_SOURCE),
            transfer(validTransfer.copy(toAccountId = CASH_ID)),
        )
    }

    @Test fun `transfer model itself refuses identical accounts`() {
        try {
            transfer(rupees(1), from = CASH_ID, to = CASH_ID)
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
        }
    }

    @Test fun `transfer accounts must both exist and be active`() {
        assertEquals(
            setOf(
                ValidationError(Field.FROM_ACCOUNT, Reason.NOT_FOUND),
                ValidationError(Field.TO_ACCOUNT, Reason.INACTIVE),
            ),
            transfer(validTransfer.copy(fromAccountId = AccountId("x"), toAccountId = closed)).toSet(),
        )
        assertEquals(
            setOf(
                ValidationError(Field.FROM_ACCOUNT, Reason.REQUIRED),
                ValidationError(Field.TO_ACCOUNT, Reason.REQUIRED),
            ),
            transfer(validTransfer.copy(fromAccountId = null, toAccountId = null)).toSet(),
        )
    }

    @Test fun `transfer requires a date`() {
        assertEquals(err(Field.DATE, Reason.REQUIRED), transfer(validTransfer.copy(date = null)))
    }
}
