package com.kumquat.ledger

import com.kumquat.ledger.domain.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun parse_simple_yuan() {
        assertEquals(1500L, Money.parseToCents("15"))
        assertEquals(1550L, Money.parseToCents("15.5"))
        assertEquals(1534L, Money.parseToCents("15.34"))
        assertEquals(50L, Money.parseToCents("0.5"))
        assertEquals(5L, Money.parseToCents(".05"))
    }

    @Test
    fun parse_invalid_returns_null() {
        assertNull(Money.parseToCents(""))
        assertNull(Money.parseToCents("abc"))
        assertNull(Money.parseToCents("1.234"))
        assertNull(Money.parseToCents("1.2.3"))
        assertNull(Money.parseToCents("-5"))
        assertNull(Money.parseToCents("0"))
        assertNull(Money.parseToCents("0.00"))
    }

    @Test
    fun format_cents() {
        assertEquals("0.00", Money.format(0))
        assertEquals("15.00", Money.format(1500))
        assertEquals("15.34", Money.format(1534))
        assertEquals("1,234.56", Money.format(123456))
        assertEquals("-1.00", Money.format(-100))
    }

    @Test
    fun input_roundtrip() {
        assertEquals("15.34", Money.toPlainInput(1534))
        assertEquals("15.3", Money.toPlainInput(1530))
        assertEquals("15", Money.toPlainInput(1500))
        assertEquals(1534L, Money.parseToCents(Money.toPlainInput(1534)))
    }

    @Test
    fun signed_format_for_huiwang_net() {
        assertEquals("¥-2.60", Money.formatSignedWithSymbol(-260))
        assertEquals("¥+3.00", Money.formatSignedWithSymbol(300))
        assertEquals("¥0.00", Money.formatSignedWithSymbol(0))
        assertEquals("¥0.00", Money.formatSignedWithSymbol(-0))
    }

    @Test
    fun signed_parse_allows_zero_and_negative() {
        assertEquals(0L, Money.parseSignedToCents("0"))
        assertEquals(0L, Money.parseSignedToCents("0.00"))
        assertNull(Money.parseSignedToCents(""))
        assertEquals(-123_45L, Money.parseSignedToCents("-123.45"))
        assertEquals(1_000_00L, Money.parseSignedToCents("1,000"))
        assertNull(Money.parseSignedToCents("-"))
        assertNull(Money.parseSignedToCents("1.234"))
    }
}
