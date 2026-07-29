package com.carddemo.interest.cobol;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ZonedTest {

    @Test
    void parsesPositiveOverpunch() {
        assertEquals(new BigDecimal("0.00"), Zoned.parseSigned("0000000000{", 2));
        assertEquals(new BigDecimal("15.00"), Zoned.parseSigned("00150{", 2));
        // the trailing overpunch is itself a digit: 0 with a + sign
        assertEquals(new BigDecimal("194.00"), Zoned.parseSigned("00000001940{", 2));
        assertEquals(new BigDecimal("1234.50"), Zoned.parseSigned("0000012345{", 2));
        assertEquals(new BigDecimal("1234.56"), Zoned.parseSigned("0000012345F", 2));
        assertEquals(new BigDecimal("100.01"), Zoned.parseSigned("000001000A", 2));
    }

    @Test
    void parsesNegativeOverpunch() {
        assertEquals(new BigDecimal("-100.01"), Zoned.parseSigned("000001000J", 2));
        assertEquals(new BigDecimal("-0.00"), Zoned.parseSigned("0000000000}", 2));
    }

    @Test
    void roundTripsSignedValues() {
        assertEquals("0000001234E", Zoned.formatSigned(new BigDecimal("123.450"), 11, 2));
        assertEquals("0000012345{", Zoned.formatSigned(new BigDecimal("1234.50"), 11, 2));
        assertEquals("000001000A", Zoned.formatSigned(new BigDecimal("100.01"), 10, 2));
        assertEquals("000001000J", Zoned.formatSigned(new BigDecimal("-100.01"), 10, 2));
        assertEquals(new BigDecimal("-100.01"),
                Zoned.parseSigned(Zoned.formatSigned(new BigDecimal("-100.01"), 12, 2), 2));
    }

    @Test
    void rejectsOverflow() {
        assertThrows(IllegalArgumentException.class, () -> Zoned.formatSigned(new BigDecimal("12345.67"), 6, 2));
        assertThrows(IllegalArgumentException.class, () -> Zoned.formatUnsigned(1234567890L, 9));
    }

    @Test
    void padsAlphanumericFields() {
        assertEquals("ab   ", Zoned.alphanumeric("ab", 5));
        assertEquals("abcde", Zoned.alphanumeric("abcdefgh", 5));
        assertEquals("     ", Zoned.alphanumeric(null, 5));
    }
}
