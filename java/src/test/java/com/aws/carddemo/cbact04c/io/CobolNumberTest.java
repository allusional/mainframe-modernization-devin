package com.aws.carddemo.cbact04c.io;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CobolNumberTest {

    @Test
    void parsesPositiveZeroOverpunch() {
        // "00000001940{" is S9(10)V99 -> 194.00 (last '{' is +0)
        assertEquals(new BigDecimal("194.00"), CobolNumber.parseSigned("00000001940{", 2));
    }

    @Test
    void parsesPositiveNonZeroOverpunch() {
        // "00150{" is S9(04)V99 -> 15.00 ; "0000000012B" -> +2 in last digit
        assertEquals(new BigDecimal("15.00"), CobolNumber.parseSigned("00150{", 2));
        assertEquals(new BigDecimal("0.02"), CobolNumber.parseSigned("000000000B", 2));
    }

    @Test
    void parsesNegativeOverpunch() {
        // last '}' -> -0 ; last 'J' -> -1
        assertEquals(new BigDecimal("-1.00"), CobolNumber.parseSigned("0000010}", 2));
        assertEquals(new BigDecimal("-0.01"), CobolNumber.parseSigned("000000J", 2));
    }

    @Test
    void formatSignedRoundTripsPositive() {
        assertEquals("00000001940{", CobolNumber.formatSigned(new BigDecimal("194.00"), 12, 2));
    }

    @Test
    void formatSignedRoundTripsNegative() {
        assertEquals("000000000000J", CobolNumber.formatSigned(new BigDecimal("-0.01"), 13, 2));
        assertEquals("00000010}", CobolNumber.formatSigned(new BigDecimal("-1.00"), 9, 2));
    }

    @Test
    void formatSignedTruncatesTowardsZero() {
        // 0.419 must truncate (not round) to 0.41 -> unscaled 41 -> "00004" + overpunch(1)='A'
        assertEquals("00004A", CobolNumber.formatSigned(new BigDecimal("0.419"), 6, 2));
    }

    @Test
    void formatAndParseUnsigned() {
        assertEquals("0005", CobolNumber.formatUnsigned(5, 4));
        assertEquals(11L, CobolNumber.parseUnsignedLong("00000000011"));
    }

    @Test
    void rejectsInvalidZoneCharacter() {
        assertThrows(IllegalArgumentException.class, () -> CobolNumber.parseSigned("0000#", 2));
    }
}
