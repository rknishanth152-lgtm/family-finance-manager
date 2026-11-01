package com.family.finance.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

    @Test fun `add and subtract are exact in paise`() {
        assertEquals(Money(30), Money(10) + Money(20))
        assertEquals(Money(-10), Money(10) - Money(20))
        // 0.1 + 0.2 is the classic floating-point trap; paise stay exact.
        assertEquals(Money.parse("0.30"), Money.parse("0.10")!! + Money.parse("0.20")!!)
    }

    @Test fun `ofRupees converts to paise`() {
        assertEquals(2_000_000L, Money.ofRupees(20_000).paise)
    }

    @Test fun `parse accepts whole, decimal and grouped amounts`() {
        assertEquals(Money(2_000_000), Money.parse("20000"))
        assertEquals(Money(125_050), Money.parse("1,250.50"))
        assertEquals(Money(150), Money.parse("1.5"))
        assertEquals(Money(5), Money.parse("0.05"))
        assertEquals(Money(100), Money.parse("1.000")) // trailing zeros carry no extra precision
        assertEquals(Money(-250), Money.parse("-2.50"))
    }

    @Test fun `parse rejects more than two decimal places instead of rounding`() {
        assertNull(Money.parse("10.005"))
        assertNull(Money.parse("0.999"))
    }

    @Test fun `parse rejects malformed input`() {
        listOf("", " ", "abc", "1.2.3", "1e3", ".5", "5.", "₹10", "NaN").forEach {
            assertNull("expected null for '$it'", Money.parse(it))
        }
    }

    @Test fun `parse rejects values beyond Long range`() {
        assertNull(Money.parse("999999999999999999999"))
    }

    @Test(expected = ArithmeticException::class)
    fun `overflow throws instead of wrapping`() {
        Money(Long.MAX_VALUE) + Money(1)
    }

    @Test fun `sign helpers`() {
        assertTrue(Money(1).isPositive)
        assertTrue(Money.ZERO.isZero)
        assertTrue(Money(-1).isNegative)
    }

    @Test fun `formatter uses rupee symbol and Indian grouping`() {
        assertEquals("₹0.00", MoneyFormatter.format(Money.ZERO))
        assertEquals("₹0.05", MoneyFormatter.format(Money(5)))
        assertEquals("₹999.00", MoneyFormatter.format(Money.ofRupees(999)))
        assertEquals("₹20,000.00", MoneyFormatter.format(Money.ofRupees(20_000)))
        assertEquals("₹1,23,45,678.90", MoneyFormatter.format(Money(1_234_567_890)))
        assertEquals("-₹1,250.50", MoneyFormatter.format(Money(-125_050)))
    }
}
