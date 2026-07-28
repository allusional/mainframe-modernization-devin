package com.carddemo.cbtrn02c.io;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZonedDecimalTest {

    @Test
    void decodesPositiveOverpunchWithImpliedDecimals() {
        // "0000005047G" -> digits 00000050477 (G = +7), V99 -> 504.77
        assertEquals(new BigDecimal("504.77"), ZonedDecimal.decodeSigned("0000005047G", 2));
    }

    @Test
    void decodesPositiveZeroOverpunch() {
        // "{" encodes a trailing +0
        assertEquals(new BigDecimal("194.00"), ZonedDecimal.decodeSigned("00000001940{", 2));
    }

    @Test
    void decodesNegativeOverpunch() {
        // "0000000012J" -> J = -1 -> 000000001 21 -> -1.21
        assertEquals(new BigDecimal("-1.21"), ZonedDecimal.decodeSigned("0000000012J", 2));
    }

    @Test
    void encodeIsInverseOfDecodeForPositive() {
        String raw = "0000005047G";
        BigDecimal value = ZonedDecimal.decodeSigned(raw, 2);
        assertEquals(raw, ZonedDecimal.encodeSigned(value, 11, 2));
    }

    @Test
    void encodeIsInverseOfDecodeForNegative() {
        BigDecimal value = new BigDecimal("-1.21");
        String encoded = ZonedDecimal.encodeSigned(value, 11, 2);
        assertEquals("0000000012J", encoded);
        assertEquals(value, ZonedDecimal.decodeSigned(encoded, 2));
    }

    @Test
    void encodeTruncatesHighOrderDigitsLikeCobolFieldMove() {
        // Value needs 4 integer digits but the field holds only 3 (+2 implied decimals).
        String encoded = ZonedDecimal.encodeSigned(new BigDecimal("1234.56"), 5, 2);
        assertEquals("2345F", encoded); // high-order '1' dropped, F = +6
    }

    @Test
    void decodesBlankFieldAsZero() {
        assertEquals(new BigDecimal("0.00"), ZonedDecimal.decodeSigned("           ", 2));
    }
}
