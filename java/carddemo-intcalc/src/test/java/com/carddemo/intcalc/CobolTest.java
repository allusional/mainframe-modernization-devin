package com.carddemo.intcalc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Tests for the fixed-width COBOL DISPLAY field codec. */
class CobolTest {

    @Test
    void decodesThePositiveZoneOverpunch() {
        assertEquals(new BigDecimal("0.00"), Cobol.decimal("0000000000{", 0, 11, 2));
        assertEquals(new BigDecimal("1234.51"), Cobol.decimal("0000012345A", 0, 11, 2));
        assertEquals(new BigDecimal("15.00"), Cobol.decimal("00150{", 0, 6, 2));
    }

    @Test
    void decodesTheNegativeZoneOverpunch() {
        assertEquals(0, Cobol.decimal("0000000000}", 0, 11, 2).signum());
        assertEquals(new BigDecimal("-1234.59"), Cobol.decimal("0000012345R", 0, 11, 2));
    }

    @Test
    void decodesAnUnsignedTrailingDigitAsPositive() {
        assertEquals(new BigDecimal("1234.55"), Cobol.decimal("00000123455", 0, 11, 2));
    }

    @Test
    void rejectsAFieldThatIsNotANumber() {
        assertThrows(IllegalArgumentException.class, () -> Cobol.decimal("000000000 x", 0, 11, 2));
    }

    @Test
    void rendersTheOverpunchBack() {
        assertEquals("0000000000{", Cobol.putDecimal(new BigDecimal("0.00"), 11, 2));
        assertEquals("0000012345A", Cobol.putDecimal(new BigDecimal("1234.51"), 11, 2));
        assertEquals("0000012345R", Cobol.putDecimal(new BigDecimal("-1234.59"), 11, 2));
        // A truncated negative zero has no sign in COBOL either: GnuCOBOL normalises it to +0.
        assertEquals("0000000000{", Cobol.putDecimal(new BigDecimal("-0.00"), 11, 2));
    }

    @Test
    void rendersTextAndDigits() {
        assertEquals("ab        ", Cobol.putText("ab", 10));
        assertEquals("abcde", Cobol.putText("abcdefgh", 5));
        assertEquals("0000000042", Cobol.putDigits(42, 10));
        assertEquals("0005", Cobol.putDigits("5", 4));
        assertEquals("2345", Cobol.putDigits("12345", 4));
    }

    @Test
    void movesTruncateInsteadOfRounding() {
        assertEquals(new BigDecimal("1.25"), Cobol.amount(new BigDecimal("1.259999")));
        assertEquals(new BigDecimal("-1.25"), Cobol.amount(new BigDecimal("-1.259999")));
    }

    @Test
    void movesDropTheHighOrderDigitsThatDoNotFit() {
        assertEquals(new BigDecimal("1.25"), Cobol.amount(new BigDecimal("1000000001.25"), 9, 2));
        assertEquals(new BigDecimal("-1.25"), Cobol.amount(new BigDecimal("-1000000001.25"), 9, 2));
        assertEquals(new BigDecimal("999999999.99"), Cobol.amount(new BigDecimal("999999999.99"), 9, 2));
    }

    @Test
    void readsTextWithoutItsTrailingSpaces() {
        assertEquals("A0", Cobol.text("xxA0        ", 2, 10));
        assertEquals("", Cobol.text("          ", 0, 10));
        assertEquals("00150{", Cobol.digits("xx00150{", 2, 6));
    }
}
