package com.family.finance.ui

import com.family.finance.data.AppContainer
import com.family.finance.domain.calc.ReportingPeriod
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
import com.family.finance.testing.MainDispatcherRule
import com.family.finance.testing.TODAY
import com.family.finance.testing.account
import com.family.finance.testing.testContainer
import com.family.finance.ui.form.TransactionFormViewModel
import com.family.finance.ui.home.HomeViewModel
import com.family.finance.ui.home.PeriodKind
import com.family.finance.ui.navigation.FormKind
import com.family.finance.ui.transactions.TransactionsViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

class ViewModelsTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val c: AppContainer = testContainer()
    private val cash = AccountId("cash")
    private val bank = AccountId("bank")
    private val salary = DefaultCategories.idFor("salary")
    private val food = DefaultCategories.idFor("food")

    private suspend fun accounts(vararg ids: String) = ids.forEach { c.accountRepository.upsert(account(it, opening = 1_000)) }

    private fun form(kind: FormKind, editId: TransactionId? = null) = TransactionFormViewModel(
        kind, editId, c.transactionService, c.transactionRepository, c.accountRepository,
        c.categoryRepository, c.entryDefaults, c::today,
    )

    private suspend fun txns() = c.transactionRepository.observeAll().first()

    // --- Transaction form ---

    @Test fun `repeated save taps create exactly one transaction`() = runTest {
        accounts("cash")
        val vm = form(FormKind.EXPENSE)
        vm.edit { it.copy(amountText = "120", title = "Tea", categoryId = food) }
        vm.save(); vm.save(); vm.save()

        assertThat(txns()).hasSize(1)
        assertThat(vm.state.value.savedId).isNotNull()
    }

    @Test fun `new form defaults to today and the only active account`() = runTest {
        accounts("cash")
        val vm = form(FormKind.INCOME)
        assertThat(vm.state.value.fields.date).isEqualTo(TODAY)
        assertThat(vm.state.value.fields.accountId).isEqualTo(cash)
    }

    @Test fun `validation errors keep everything the user typed`() = runTest {
        accounts("cash", "bank")
        val vm = form(FormKind.INCOME)
        vm.edit { it.copy(amountText = "10.555", title = "சம்பளம்", source = "Office") }
        vm.save()

        val s = vm.state.value
        assertThat(s.fields.amountText).isEqualTo("10.555")
        assertThat(s.fields.title).isEqualTo("சம்பளம்")
        // Category and account may be left blank; only the bad amount stops the save.
        assertThat(s.errors).containsExactly(ValidationError(Field.AMOUNT, Reason.INVALID_FORMAT))
        assertThat(txns()).isEmpty()
    }

    @Test fun `an entry with only amount, title and date can be saved`() = runTest {
        accounts("cash")
        val vm = form(FormKind.EXPENSE)
        vm.edit { it.copy(amountText = "50", title = "Tea") }
        vm.save()
        assertThat(vm.state.value.errors).isEmpty()
        assertThat(txns()).hasSize(1)
    }

    @Test fun `changing a field clears only that field's error`() = runTest {
        accounts("cash")
        val vm = form(FormKind.EXPENSE)
        vm.save()
        assertThat(vm.state.value.errors.map { it.field }).containsExactly(Field.AMOUNT, Field.TITLE)

        vm.edit { it.copy(amountText = "50") }
        assertThat(vm.state.value.errors.map { it.field }).doesNotContain(Field.AMOUNT)
        assertThat(vm.state.value.errors.map { it.field }).contains(Field.TITLE)
    }

    @Test fun `next form remembers the last account and category`() = runTest {
        accounts("cash", "bank")
        val first = form(FormKind.INCOME)
        first.edit { it.copy(amountText = "500", title = "Milk sale", categoryId = salary, source = "Dairy", accountId = bank) }
        first.save()

        val next = form(FormKind.INCOME)
        assertThat(next.state.value.fields.accountId).isEqualTo(bank)
        assertThat(next.state.value.fields.categoryId).isEqualTo(salary)
        // Expense forms keep their own memory.
        assertThat(form(FormKind.EXPENSE).state.value.fields.categoryId).isNull()
    }

    @Test fun `add another starts a new record but keeps account, category and date`() = runTest {
        accounts("cash")
        val vm = form(FormKind.EXPENSE)
        vm.edit { it.copy(amountText = "40", title = "Bus", categoryId = food) }
        vm.save()
        vm.addAnother()
        vm.edit { it.copy(amountText = "60", title = "Lunch") }
        vm.save()

        assertThat(txns()).hasSize(2)
        assertThat(vm.state.value.fields.categoryId).isEqualTo(food)
    }

    @Test fun `editing loads the saved values and updates the same record`() = runTest {
        accounts("cash")
        val id = TransactionId("e1")
        c.transactionService.saveExpense(id, ExpenseDraft("250.50", "Tea", food, TODAY, cash, notes = "டீ"))

        val vm = form(FormKind.EXPENSE, editId = id)
        assertThat(vm.state.value.fields.amountText).isEqualTo("250.50")
        assertThat(vm.state.value.fields.notes).isEqualTo("டீ")
        assertThat(vm.state.value.showMore).isTrue()

        vm.edit { it.copy(amountText = "300") }
        vm.save()
        assertThat(txns().single().amount).isEqualTo(Money.ofRupees(300))
    }

    @Test fun `transfer form rejects the same account on both sides`() = runTest {
        accounts("cash", "bank")
        val vm = form(FormKind.TRANSFER)
        vm.edit { it.copy(amountText = "100", accountId = cash, toAccountId = cash) }
        vm.save()
        assertThat(vm.state.value.errors).contains(ValidationError(Field.TO_ACCOUNT, Reason.SAME_AS_SOURCE))
        assertThat(txns()).isEmpty()
    }

    // --- Home ---

    /** Keeps a subscriber alive so WhileSubscribed flows run; tests then assert `.value`. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun TestScope.observe(flow: StateFlow<*>) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect {} }
    }

    @Test fun `home summary updates when a transaction is added and ignores transfers`() = runTest {
        accounts("cash", "bank")
        val vm = HomeViewModel(c.transactionRepository, c.accountRepository, c.categoryRepository, c::today)
        observe(vm.uiState)
        assertThat(vm.uiState.value.summary!!.totalIncome).isEqualTo(Money.ZERO)

        c.transactionService.saveIncome(TransactionId("i1"), IncomeDraft("5000", "Rice", salary, "Mill", TODAY, cash))
        val afterIncome = vm.uiState.value
        assertThat(afterIncome.summary!!.totalIncome).isEqualTo(Money.ofRupees(5_000))
        assertThat(afterIncome.currentBalance).isEqualTo(Money.ofRupees(7_000))
        assertThat(afterIncome.recent.map(Transaction::id)).containsExactly(TransactionId("i1"))

        c.transactionService.saveTransfer(TransactionId("t1"), TransferDraft("500", cash, bank, TODAY))
        val afterTransfer = vm.uiState.value
        assertThat(afterTransfer.summary!!.totalIncome).isEqualTo(Money.ofRupees(5_000))
        assertThat(afterTransfer.summary.totalExpense).isEqualTo(Money.ZERO)
        assertThat(afterTransfer.currentBalance).isEqualTo(Money.ofRupees(7_000))
        assertThat(afterTransfer.recent).hasSize(2)
    }

    @Test fun `home period navigation`() = runTest {
        val vm = HomeViewModel(c.transactionRepository, c.accountRepository, c.categoryRepository, c::today)
        observe(vm.uiState)
        assertThat(vm.uiState.value.period).isEqualTo(ReportingPeriod.Month(YearMonth.from(TODAY)))
        vm.previousPeriod()
        assertThat(vm.uiState.value.period).isEqualTo(ReportingPeriod.Month(YearMonth.from(TODAY).minusMonths(1)))
        vm.selectPeriod(PeriodKind.TODAY)
        assertThat(vm.uiState.value.period).isEqualTo(ReportingPeriod.Day(TODAY))
    }

    // --- Transactions list ---

    @Test fun `transactions screen searches localized category names`() = runTest {
        accounts("cash")
        c.transactionService.saveExpense(TransactionId("e1"), ExpenseDraft("80", "Tea", food, TODAY, cash))
        c.transactionService.saveExpense(TransactionId("e2"), ExpenseDraft("900", "Diesel", DefaultCategories.idFor("transport"), TODAY, cash))
        val vm = TransactionsViewModel(c.transactionRepository, c.accountRepository, c.categoryRepository)
        observe(vm.uiState)
        assertThat(vm.uiState.value.items).hasSize(2)

        vm.setCategoryNames(mapOf(food to "உணவு"))
        vm.updateFilter { it.copy(text = "உணவு") }
        assertThat(vm.uiState.value.items.map(Transaction::id)).containsExactly(TransactionId("e1"))

        vm.updateFilter { it.copy(text = "") }
        assertThat(vm.uiState.value.items).hasSize(2)
    }
}
