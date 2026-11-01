package com.family.finance.domain.calc

import com.family.finance.domain.Fixtures.BANK_ID
import com.family.finance.domain.Fixtures.CASH_ID
import com.family.finance.domain.Fixtures.account
import com.family.finance.domain.Fixtures.date
import com.family.finance.domain.Fixtures.expense
import com.family.finance.domain.Fixtures.income
import com.family.finance.domain.Fixtures.rupees
import com.family.finance.domain.Fixtures.transfer
import com.family.finance.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class BalanceCalculatorTest {

    private val cash = account(CASH_ID, opening = rupees(50_000))
    private val bank = account(BANK_ID, opening = rupees(100_000))
    private val accounts = listOf(cash, bank)

    @Test fun `balance with no transactions is the opening balance`() {
        assertEquals(rupees(50_000), BalanceCalculator.accountBalance(cash, emptyList()))
    }

    @Test fun `income increases the account balance`() {
        val txns = listOf(income(rupees(5_000), CASH_ID))
        assertEquals(rupees(55_000), BalanceCalculator.accountBalance(cash, txns))
        assertEquals(rupees(100_000), BalanceCalculator.accountBalance(bank, txns))
    }

    @Test fun `expense decreases the account balance`() {
        val txns = listOf(expense(rupees(1_200), CASH_ID))
        assertEquals(rupees(48_800), BalanceCalculator.accountBalance(cash, txns))
        assertEquals(rupees(100_000), BalanceCalculator.accountBalance(bank, txns))
    }

    @Test fun `transfer of 20000 moves money between accounts but not the combined balance`() {
        val before = BalanceCalculator.combinedBalance(accounts, emptyList())
        val txns = listOf(transfer(rupees(20_000), from = CASH_ID, to = BANK_ID))

        assertEquals(rupees(30_000), BalanceCalculator.accountBalance(cash, txns))
        assertEquals(rupees(120_000), BalanceCalculator.accountBalance(bank, txns))
        assertEquals(before, BalanceCalculator.combinedBalance(accounts, txns))
        assertEquals(rupees(150_000), BalanceCalculator.combinedBalance(accounts, txns))
    }

    @Test fun `combined balance follows the PRD formula across mixed transactions`() {
        val txns = listOf(
            income(rupees(10_000), BANK_ID),
            expense(rupees(2_500), CASH_ID),
            transfer(rupees(20_000), from = BANK_ID, to = CASH_ID),
            expense(Money(99), BANK_ID),
        )
        // cash: 50,000 - 2,500 + 20,000 = 67,500
        assertEquals(rupees(67_500), BalanceCalculator.accountBalance(cash, txns))
        // bank: 100,000 + 10,000 - 20,000 - 0.99 = 89,999.01
        assertEquals(Money(8_999_901), BalanceCalculator.accountBalance(bank, txns))
        // combined: 150,000 + 10,000 - 2,500 - 0.99
        assertEquals(Money(15_749_901), BalanceCalculator.combinedBalance(accounts, txns))
    }

    @Test fun `balance as of a date ignores later transactions`() {
        val txns = listOf(
            income(rupees(1_000), CASH_ID, on = "2026-03-10"),
            income(rupees(2_000), CASH_ID, on = "2026-03-11"),
        )
        assertEquals(rupees(51_000), BalanceCalculator.accountBalance(cash, txns, asOf = date("2026-03-10")))
        assertEquals(rupees(53_000), BalanceCalculator.accountBalance(cash, txns, asOf = date("2026-03-11")))
    }

    @Test fun `opening balance counts only from its opening date`() {
        val wallet = account(CASH_ID, opening = rupees(700), openingDate = date("2026-06-01"))
        assertEquals(Money.ZERO, BalanceCalculator.accountBalance(wallet, emptyList(), asOf = date("2026-05-31")))
        assertEquals(rupees(700), BalanceCalculator.accountBalance(wallet, emptyList(), asOf = date("2026-06-01")))
    }
}
