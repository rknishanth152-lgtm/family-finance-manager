package com.family.finance.domain.usecase

import com.family.finance.domain.Fixtures.BANK_ID
import com.family.finance.domain.Fixtures.CASH_ID
import com.family.finance.domain.Fixtures.GROCERIES_ID
import com.family.finance.domain.Fixtures.date
import com.family.finance.domain.Fixtures.expense
import com.family.finance.domain.Fixtures.income
import com.family.finance.domain.Fixtures.rupees
import com.family.finance.domain.Fixtures.transfer
import com.family.finance.domain.calc.DateRange
import com.family.finance.domain.model.Transaction
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.text.Normalizer

class TransactionQueryTest {

    private val salary = income(rupees(30_000), BANK_ID, on = "2026-03-01")
    private val groceries = expense(rupees(1_200), CASH_ID, on = "2026-03-05").copy(title = "காய்கறி வாங்குதல்", merchant = "Ravi Stores")
    private val seeds = expense(rupees(800), BANK_ID, on = "2026-03-10").copy(title = "Seeds", notes = "நெல் விதை")
    private val move = transfer(rupees(20_000), from = CASH_ID, to = BANK_ID, on = "2026-03-12")
    private val all = listOf(salary, groceries, seeds, move)

    @Test fun `type filter separates income, expense and transfer`() {
        assertThat(TransactionQuery(type = TypeFilter.INCOME).apply(all)).containsExactly(salary)
        assertThat(TransactionQuery(type = TypeFilter.EXPENSE).apply(all)).containsExactly(groceries, seeds)
        assertThat(TransactionQuery(type = TypeFilter.TRANSFER).apply(all)).containsExactly(move)
        assertThat(TransactionQuery().apply(all)).hasSize(4)
    }

    @Test fun `search finds Tamil text in titles and notes`() {
        assertThat(TransactionQuery(text = "காய்கறி").apply(all)).containsExactly(groceries)
        assertThat(TransactionQuery(text = "விதை").apply(all)).containsExactly(seeds)
    }

    @Test fun `search matches Tamil typed in a different Unicode form`() {
        // "கொ" can be typed as one precomposed sequence or as base + two-part vowel sign.
        val decomposed = Normalizer.normalize("கொத்தமல்லி", Normalizer.Form.NFD)
        val t = expense(rupees(20), CASH_ID).copy(title = "கொத்தமல்லி")
        assertThat(TransactionQuery(text = decomposed).apply(listOf(t))).containsExactly(t)
    }

    @Test fun `search is case-insensitive and covers merchant and category name`() {
        assertThat(TransactionQuery(text = "ravi").apply(all)).containsExactly(groceries)
        val byCategory = TransactionQuery(text = "மளிகை").apply(all) { if (it == GROCERIES_ID) "மளிகைப் பொருட்கள்" else "" }
        assertThat(byCategory).containsExactly(groceries, seeds)
    }

    @Test fun `account filter includes both sides of a transfer`() {
        assertThat(TransactionQuery(accountId = CASH_ID).apply(all)).containsExactly(groceries, move)
        assertThat(TransactionQuery(accountId = BANK_ID).apply(all)).containsExactly(salary, seeds, move)
    }

    @Test fun `amount range is inclusive`() {
        val result = TransactionQuery(minAmount = rupees(800), maxAmount = rupees(1_200)).apply(all)
        assertThat(result).containsExactly(groceries, seeds)
    }

    @Test fun `date range is inclusive of both ends`() {
        val result = TransactionQuery(range = DateRange(date("2026-03-05"), date("2026-03-10"))).apply(all)
        assertThat(result).containsExactly(groceries, seeds)
    }

    @Test fun `sorting by date and amount`() {
        assertThat(TransactionQuery(sort = SortOrder.DATE_DESC).apply(all)).containsExactly(move, seeds, groceries, salary).inOrder()
        assertThat(TransactionQuery(sort = SortOrder.DATE_ASC).apply(all)).containsExactly(salary, groceries, seeds, move).inOrder()
        assertThat(TransactionQuery(sort = SortOrder.AMOUNT_DESC).apply(all).map(Transaction::amount))
            .containsExactly(rupees(30_000), rupees(20_000), rupees(1_200), rupees(800)).inOrder()
    }

    @Test fun `blank search matches everything`() {
        assertThat(TransactionQuery(text = "   ").apply(all)).hasSize(4)
    }
}
