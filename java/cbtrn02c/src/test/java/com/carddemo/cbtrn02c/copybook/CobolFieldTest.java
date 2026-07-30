package com.carddemo.cbtrn02c.copybook;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CobolFieldTest {

    @ParameterizedTest
    @CsvSource({
            "0000005047G, 504.77",
            "0000005047P, -504.77",
            "0000000000{, 0.00",
            "0000000000}, 0.00",
            "0000000000N, -0.05",
            "0000009190}, -919.00",
            "0000005047A, 504.71",
            "0000005047I, 504.79",
            "0000005047J, -504.71",
            "0000005047R, -504.79",
    })
    void parsesTrailingOverpunchSign(String image, BigDecimal expected) {
        assertEquals(expected.setScale(2), CobolField.signed(image, 0, 11, 2));
    }

    @ParameterizedTest
    @CsvSource({
            "504.77, 0000005047G",
            "-504.77, 0000005047P",
            "0.00, 0000000000{",
            "-0.05, 0000000000N",
            "-919.00, 0000009190}",
    })
    void writesTrailingOverpunchSign(BigDecimal value, String expected) {
        assertEquals(expected, CobolField.formatSigned(value, 11, 2));
    }

    @Test
    void signedRoundTripIsLossless() {
        String image = "0000009190}";
        assertEquals(image, CobolField.formatSigned(CobolField.signed(image, 0, 11, 2), 11, 2));
    }

    @Test
    void unsignedFieldsAreScaledDigits() {
        assertEquals(new BigDecimal("1234.56"), CobolField.unsigned("000123456", 0, 9, 2));
        assertEquals("0001", CobolField.digits("00000000001010001", 13, 4));
    }

    @Test
    void truncationDropsFractionAndHighOrderDigitsLikeCobol() {
        // excess decimals are truncated towards zero, not rounded
        assertEquals(new BigDecimal("1.23"), CobolField.truncate(new BigDecimal("1.239"), 11, 2));
        assertEquals(new BigDecimal("-1.23"), CobolField.truncate(new BigDecimal("-1.239"), 11, 2));
        // S9(09)V99 holds 9 integer digits: the 10th is lost, as in COMPUTE without ON SIZE ERROR
        assertEquals(new BigDecimal("1.00"), CobolField.truncate(new BigDecimal("1000000001.00"), 11, 2));
    }

    @Test
    void moveAlphaPadsAndTruncates() {
        assertEquals("AB   ", CobolField.moveAlpha("AB", 5));
        assertEquals("ABCDE", CobolField.moveAlpha("ABCDEFG", 5));
    }
}
