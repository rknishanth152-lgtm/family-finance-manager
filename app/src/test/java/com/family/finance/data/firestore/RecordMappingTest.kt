package com.family.finance.data.firestore

import com.family.finance.domain.Fixtures
import com.family.finance.domain.Fixtures.BANK_ID
import com.family.finance.domain.Fixtures.CASH_ID
import com.family.finance.domain.Fixtures.rupees
import com.family.finance.domain.model.Category
import com.family.finance.domain.model.CategoryId
import com.family.finance.domain.model.CategoryKind
import com.family.finance.domain.model.DefaultCategories
import com.family.finance.domain.model.Money
import com.family.finance.domain.model.NoteEntry
import com.family.finance.domain.model.NoteId
import com.family.finance.domain.model.Transaction
import com.family.finance.domain.model.UserId
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class RecordMappingTest {

    @Test fun `every transaction type survives a round trip unchanged`() {
        val income = Fixtures.income(rupees(348_26_190), BANK_ID).copy(notes = "நெல் விற்பனை\nஇரண்டாம் வரி")
        val expense = Fixtures.expense(Money(265_050), CASH_ID).copy(merchant = "ஸ்ரீ முருகன் உரக்கடை")
        val transfer = Fixtures.transfer(rupees(20_000), from = BANK_ID, to = CASH_ID)
        listOf<Transaction>(income, expense, transfer).forEach { t ->
            assertThat(RecordMapping.decodeTransaction(t.id.value, RecordMapping.encode(t))).isEqualTo(t)
        }
    }

    @Test fun `money is stored as whole paise and dates as plain text`() {
        val t = Fixtures.expense(Money(265_050), CASH_ID).copy(date = LocalDate.parse("2026-03-31"))
        val fields = RecordMapping.encode(t)
        assertThat(fields["amountPaise"]).isEqualTo(265_050L)
        assertThat(fields["amountPaise"]).isInstanceOf(Long::class.javaObjectType)
        assertThat(fields["date"]).isEqualTo("2026-03-31")
        assertThat(fields["type"]).isEqualTo("expense")
    }

    @Test fun `field names match what the security rules check`() {
        val transfer = RecordMapping.encode(Fixtures.transfer(rupees(1), from = CASH_ID, to = BANK_ID))
        assertThat(transfer.keys).containsAtLeast("type", "amountPaise", "date", "title", "fromAccountId", "toAccountId", "createdBy", "createdAt", "updatedAt")
        val account = RecordMapping.encode(Fixtures.account(CASH_ID, opening = rupees(-50_000)))
        assertThat(account.keys).containsAtLeast("name", "openingBalancePaise", "openingBalanceDate", "isActive")
        assertThat(account["openingBalancePaise"]).isEqualTo(-5_000_000L)
    }

    @Test fun `numbers read back as Long even if Firestore returns another number type`() {
        val fields = RecordMapping.encode(Fixtures.income(rupees(5), CASH_ID)).toMutableMap()
        fields["amountPaise"] = 500.0 // defensive: never expected, but never misread either
        assertThat(RecordMapping.decodeTransaction("t", fields).amount).isEqualTo(Money(500))
    }

    @Test fun `amounts beyond the app limit are refused, so one bad record cannot break every total`() {
        val fields = RecordMapping.encode(Fixtures.income(rupees(5), CASH_ID)).toMutableMap()
        fields["amountPaise"] = Money.MAX_AMOUNT_PAISE
        assertThat(RecordMapping.decodeTransaction("t", fields).amount).isEqualTo(Money(Money.MAX_AMOUNT_PAISE))
        for (bad in listOf(Money.MAX_AMOUNT_PAISE + 1, Long.MAX_VALUE, 0L, -100L)) {
            fields["amountPaise"] = bad
            assertThat(runCatching { RecordMapping.decodeTransaction("t", fields) }.isFailure).isTrue()
        }
        val account = RecordMapping.encode(Fixtures.account(BANK_ID)).toMutableMap()
        account["openingBalancePaise"] = Long.MIN_VALUE
        assertThat(runCatching { RecordMapping.decodeAccount("a", account) }.isFailure).isTrue()
    }

    @Test fun `accounts, categories and notes round trip`() {
        val account = Fixtures.account(BANK_ID, opening = Money(19_685_075))
        assertThat(RecordMapping.decodeAccount(account.id.value, RecordMapping.encode(account))).isEqualTo(account)

        val renamedBuiltIn = DefaultCategories.all.first { it.systemKey == "seeds" }.copy(name = "நெல் விதை", isArchived = true)
        assertThat(RecordMapping.decodeCategory(renamedBuiltIn.id.value, RecordMapping.encode(renamedBuiltIn))).isEqualTo(renamedBuiltIn)
        val custom = Category(CategoryId("c1"), "Tractor diesel", CategoryKind.EXPENSE, isArchived = false)
        assertThat(RecordMapping.decodeCategory("c1", RecordMapping.encode(custom))).isEqualTo(custom)

        val note = NoteEntry(NoteId("n1"), "உர விலை", "யூரியா ₹265", LocalDate.parse("2026-11-01"), UserId("mother"),
            Instant.parse("2026-11-01T04:00:00.123456789Z"), Instant.parse("2026-11-01T05:00:00Z"))
        assertThat(RecordMapping.decodeNote("n1", RecordMapping.encode(note))).isEqualTo(note)
    }
}
