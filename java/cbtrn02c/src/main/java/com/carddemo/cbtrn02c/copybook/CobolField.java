package com.carddemo.cbtrn02c.copybook;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Fixed width field accessors that reproduce COBOL {@code USAGE DISPLAY} semantics.
 *
 * <p>Signed numeric fields ({@code PIC S9(n)V99}) carry their sign as an overpunch in the
 * trailing digit, using the mainframe (EBCDIC) convention that the CardDemo ASCII fixtures were
 * translated with: {@code {}=+0, A-I=+1..+9, }=-0, J-R=-1..-9. GnuCOBOL reproduces this with
 * {@code -fsign=EBCDIC}.
 */
public final class CobolField {

    private static final String POSITIVE_OVERPUNCH = "{ABCDEFGHI";
    private static final String NEGATIVE_OVERPUNCH = "}JKLMNOPQR";

    private CobolField() {
    }

    /** Alphanumeric {@code PIC X(len)} field, returned verbatim. */
    public static String alpha(String raw, int offset, int length) {
        return raw.substring(offset, offset + length);
    }

    /** Unsigned numeric {@code PIC 9(len)} field, returned as its digit string (keys, codes). */
    public static String digits(String raw, int offset, int length) {
        return raw.substring(offset, offset + length);
    }

    /** Unsigned numeric {@code PIC 9(len-scale)V9(scale)} field. */
    public static BigDecimal unsigned(String raw, int offset, int length, int scale) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = offset; i < offset + length; i++) {
            sb.append(digitOf(raw.charAt(i)));
        }
        return new BigDecimal(new BigInteger(sb.toString()), scale);
    }

    /** Signed numeric {@code PIC S9(len-scale)V9(scale)} field with a trailing overpunch sign. */
    public static BigDecimal signed(String raw, int offset, int length, int scale) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = offset; i < offset + length - 1; i++) {
            sb.append(digitOf(raw.charAt(i)));
        }
        char last = raw.charAt(offset + length - 1);
        boolean negative = NEGATIVE_OVERPUNCH.indexOf(last) >= 0;
        sb.append(lastDigitOf(last));
        BigDecimal value = new BigDecimal(new BigInteger(sb.toString()), scale);
        return negative ? value.negate() : value;
    }

    /** Serializes an unsigned numeric field, truncating high order digits like COBOL does. */
    public static String formatUnsigned(BigDecimal value, int length, int scale) {
        String digits = absoluteDigits(value, length, scale);
        return digits;
    }

    /** Serializes a signed numeric field with the trailing overpunch sign. */
    public static String formatSigned(BigDecimal value, int length, int scale) {
        String digits = absoluteDigits(value, length, scale);
        int last = digits.charAt(length - 1) - '0';
        char overpunch = value.signum() < 0
                ? NEGATIVE_OVERPUNCH.charAt(last)
                : POSITIVE_OVERPUNCH.charAt(last);
        return digits.substring(0, length - 1) + overpunch;
    }

    /**
     * Truncates a value to the capacity of a COBOL field, mirroring an arithmetic statement
     * without {@code ON SIZE ERROR}: excess fractional digits are dropped towards zero and
     * high order digits that do not fit are lost.
     */
    public static BigDecimal truncate(BigDecimal value, int length, int scale) {
        BigDecimal scaled = value.setScale(scale, RoundingMode.DOWN);
        BigDecimal capacity = BigDecimal.ONE.movePointRight(length - scale);
        return scaled.abs().compareTo(capacity) < 0 ? scaled : scaled.remainder(capacity);
    }

    /** Right pads an alphanumeric value with spaces / truncates it, like a COBOL {@code MOVE}. */
    public static String moveAlpha(String value, int length) {
        if (value.length() >= length) {
            return value.substring(0, length);
        }
        return value + " ".repeat(length - value.length());
    }

    private static String absoluteDigits(BigDecimal value, int length, int scale) {
        BigDecimal truncated = truncate(value, length, scale).abs();
        String digits = truncated.movePointRight(scale).toBigInteger().toString();
        if (digits.length() > length) {
            digits = digits.substring(digits.length() - length);
        }
        return "0".repeat(length - digits.length()) + digits;
    }

    private static char digitOf(char c) {
        if (c >= '0' && c <= '9') {
            return c;
        }
        if (c == ' ') {
            return '0';
        }
        throw new IllegalArgumentException("not a numeric display digit: '" + c + "'");
    }

    private static char lastDigitOf(char c) {
        if (c >= '0' && c <= '9') {
            return c;
        }
        if (c == ' ') {
            return '0';
        }
        int positive = POSITIVE_OVERPUNCH.indexOf(c);
        if (positive >= 0) {
            return (char) ('0' + positive);
        }
        int negative = NEGATIVE_OVERPUNCH.indexOf(c);
        if (negative >= 0) {
            return (char) ('0' + negative);
        }
        throw new IllegalArgumentException("not a signed display digit: '" + c + "'");
    }
}
