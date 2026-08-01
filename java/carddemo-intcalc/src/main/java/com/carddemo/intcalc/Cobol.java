package com.carddemo.intcalc;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Fixed-width COBOL DISPLAY field codec, matching the sample datasets in {@code app/data}:
 * {@code PIC X(n)} is space padded on the right, {@code PIC 9(n)} is zero padded on the left and
 * {@code PIC S9(n)V99} carries its sign as a zone overpunch in the last byte
 * ({@code {}=+0, A-I=+1..9, }=-0, J-R=-1..9), the representation GnuCOBOL produces with
 * {@code -fsign=EBCDIC} and z/OS COBOL produces natively.
 */
public final class Cobol {

    private static final String POSITIVE = "{ABCDEFGHI";
    private static final String NEGATIVE = "}JKLMNOPQR";

    private Cobol() {
    }

    /** PIC X(n) / PIC 9(n) taken verbatim, trailing spaces removed. */
    public static String text(String record, int offset, int length) {
        return rtrim(record.substring(offset, offset + length));
    }

    /** PIC 9(n) with no sign, as digits. */
    public static String digits(String record, int offset, int length) {
        return record.substring(offset, offset + length);
    }

    /** Signed PIC S9(n)V(scale) with a trailing overpunch sign. */
    public static BigDecimal decimal(String record, int offset, int length, int scale) {
        String field = record.substring(offset, offset + length);
        char last = field.charAt(length - 1);
        int digit = POSITIVE.indexOf(last);
        boolean negative = false;
        if (digit < 0) {
            digit = NEGATIVE.indexOf(last);
            negative = digit >= 0;
        }
        if (digit < 0) {
            if (last < '0' || last > '9') {
                throw new IllegalArgumentException("not a signed COBOL number: '" + field + "'");
            }
            digit = last - '0';
        }
        String unsigned = field.substring(0, length - 1) + digit;
        BigDecimal value = new BigDecimal(new java.math.BigInteger(unsigned), scale);
        return negative ? value.negate() : value;
    }

    /** Renders PIC X(n): left justified, space padded, truncated when too long. */
    public static String putText(String value, int length) {
        String text = value == null ? "" : value;
        if (text.length() >= length) {
            return text.substring(0, length);
        }
        return text + " ".repeat(length - text.length());
    }

    /** Renders PIC 9(n): right justified, zero padded. */
    public static String putDigits(String value, int length) {
        String text = value == null ? "" : value.trim();
        if (text.length() >= length) {
            return text.substring(text.length() - length);
        }
        return "0".repeat(length - text.length()) + text;
    }

    /** Renders PIC 9(n) from a number. */
    public static String putDigits(long value, int length) {
        return putDigits(Long.toString(value), length);
    }

    /** Renders signed PIC S9(n)V(scale) with the trailing overpunch sign. */
    public static String putDecimal(BigDecimal value, int length, int scale) {
        BigDecimal scaled = amount(value, scale);
        boolean negative = scaled.signum() < 0;
        String unsigned = scaled.abs().unscaledValue().toString();
        if (unsigned.length() > length) {
            unsigned = unsigned.substring(unsigned.length() - length);
        }
        unsigned = "0".repeat(length - unsigned.length()) + unsigned;
        int last = unsigned.charAt(length - 1) - '0';
        char sign = negative ? NEGATIVE.charAt(last) : POSITIVE.charAt(last);
        return unsigned.substring(0, length - 1) + sign;
    }

    /** A COBOL MOVE into a PIC S9(n)V99 field: truncation, never rounding. */
    public static BigDecimal amount(BigDecimal value) {
        return amount(value, 2);
    }

    /** A COBOL MOVE into a PIC S9(n)V(scale) field: truncation, never rounding. */
    public static BigDecimal amount(BigDecimal value, int scale) {
        return (value == null ? BigDecimal.ZERO : value).setScale(scale, RoundingMode.DOWN);
    }

    /**
     * A COBOL MOVE into a PIC S9(intDigits)V(scale) field: the low order digits are kept and the
     * high order ones dropped, as an unrounded, unguarded COBOL MOVE or COMPUTE does.
     */
    public static BigDecimal amount(BigDecimal value, int intDigits, int scale) {
        BigDecimal scaled = amount(value, scale);
        BigDecimal limit = BigDecimal.TEN.pow(intDigits);
        return scaled.abs().compareTo(limit) < 0 ? scaled : scaled.remainder(limit);
    }

    private static String rtrim(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == ' ') {
            end--;
        }
        return value.substring(0, end);
    }
}
