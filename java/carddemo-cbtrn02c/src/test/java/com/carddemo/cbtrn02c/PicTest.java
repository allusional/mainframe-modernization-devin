package com.carddemo.cbtrn02c;

import com.carddemo.cbtrn02c.copybook.Pic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PicTest {

    @ParameterizedTest
    @CsvSource({
            // PIC S9(09)V99 field, decoded value, canonical re-encoding
            "0000001000{,    100.00, 0000001000{",
            "0000000052E,    5.25,   0000000052E",
            "0000000400},   -40.00,  0000000400}",
            "0000001000A,    100.01, 0000001000A",
            "0000000000{,    0.00,   0000000000{",
            "0000000000I,    0.09,   0000000000I",
            "0000000000R,   -0.09,   0000000000R",
            // An unpunched trailing digit (zone F) reads as positive and is
            // rewritten with the overpunch, exactly as the COBOL runtime does.
            "00000000000,    0.00,   0000000000{",
    })
    void decodesAndReencodesOverpunchedSignedFields(String field, BigDecimal expected, String reencoded) {
        assertEquals(expected, Pic.decodeSigned(field, 2));
        assertEquals(reencoded, Pic.encodeSigned(expected, 9, 2));
    }

    @Test
    void encodesTheWiderAccountAmountPicture() {
        assertEquals("00000010000{", Pic.encodeSigned(new BigDecimal("1000.00"), 10, 2));
        assertEquals("00000003152E", Pic.encodeSigned(new BigDecimal("315.25"), 10, 2));
        assertEquals("00000000100}", Pic.encodeSigned(new BigDecimal("-10.00"), 10, 2));
    }

    @Test
    void roundTripsEveryLowOrderDigitAndSign() {
        for (int cents = -99; cents <= 99; cents++) {
            BigDecimal value = new BigDecimal(cents).movePointLeft(2);
            String encoded = Pic.encodeSigned(value, 9, 2);
            assertEquals(11, encoded.length());
            assertEquals(value, Pic.decodeSigned(encoded, 2));
        }
    }

    @Test
    void unsignedFieldsAreZeroPaddedDigits() {
        assertEquals("00000000042", Pic.encodeUnsigned(42, 11));
        assertEquals(42L, Pic.decodeUnsigned("00000000042"));
        assertEquals(0L, Pic.decodeUnsigned("           "));
    }

    @Test
    void alphanumericFieldsAreSpacePaddedAndTruncated() {
        assertEquals("AB   ", Pic.text("AB", 5));
        assertEquals("ABCDE", Pic.text("ABCDEFG", 5));
        assertEquals("     ", Pic.text(null, 5));
    }

    @Test
    void rejectsFieldsThatAreNotDisplayNumeric() {
        assertThrows(IllegalArgumentException.class, () -> Pic.decodeSigned("0000000000*", 2));
    }
}
