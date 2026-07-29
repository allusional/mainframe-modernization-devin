package com.carddemo.interest.cobol;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Conversions for COBOL display (zoned decimal) numeric fields as they appear in the
 * CardDemo flat files: {@code n} digit characters where, for signed fields, the last
 * character carries the sign as an overpunch.
 *
 * <p>Positive zero..nine are encoded {@code { A B C D E F G H I} and negative
 * zero..nine are encoded {@code } J K L M N O P Q R}.
 */
public final class Zoned {

    private static final String POSITIVE_OVERPUNCH = "{ABCDEFGHI";
    private static final String NEGATIVE_OVERPUNCH = "}JKLMNOPQR";

    private Zoned() {
    }

    /** Parses a signed display field, e.g. {@code "0000000150{"} with scale 2 -> 15.00. */
    public static BigDecimal parseSigned(String field, int scale) {
        String digits = field.trim();
        if (digits.isEmpty()) {
            return BigDecimal.ZERO.setScale(scale);
        }
        char last = digits.charAt(digits.length() - 1);
        String leading = digits.substring(0, digits.length() - 1);
        boolean negative = false;
        char lastDigit;

        int positive = POSITIVE_OVERPUNCH.indexOf(last);
        int minus = NEGATIVE_OVERPUNCH.indexOf(last);
        if (positive >= 0) {
            lastDigit = (char) ('0' + positive);
        } else if (minus >= 0) {
            negative = true;
            lastDigit = (char) ('0' + minus);
        } else if (Character.isDigit(last)) {
            lastDigit = last;
        } else {
            throw new IllegalArgumentException("Not a zoned decimal field: '" + field + "'");
        }

        BigInteger unscaled = new BigInteger(leading.isEmpty() ? String.valueOf(lastDigit) : leading + lastDigit);
        BigDecimal value = new BigDecimal(unscaled, scale);
        return negative ? value.negate() : value;
    }

    /** Parses an unsigned display field, e.g. an 11 digit account id. */
    public static long parseUnsigned(String field) {
        String digits = field.trim();
        return digits.isEmpty() ? 0L : Long.parseLong(digits);
    }

    /** Renders a signed value back into an {@code length} character zoned field. */
    public static String formatSigned(BigDecimal value, int length, int scale) {
        BigDecimal scaled = value.setScale(scale, java.math.RoundingMode.DOWN);
        boolean negative = scaled.signum() < 0;
        String digits = scaled.abs().unscaledValue().toString();
        if (digits.length() > length) {
            throw new IllegalArgumentException("Value " + value + " overflows a PIC S9(" + (length - scale)
                    + ")V9(" + scale + ") field");
        }
        digits = "0".repeat(length - digits.length()) + digits;
        int lastDigit = digits.charAt(length - 1) - '0';
        char overpunch = negative ? NEGATIVE_OVERPUNCH.charAt(lastDigit) : POSITIVE_OVERPUNCH.charAt(lastDigit);
        return digits.substring(0, length - 1) + overpunch;
    }

    /** Renders an unsigned integer into a zero filled {@code length} character field. */
    public static String formatUnsigned(long value, int length) {
        String digits = Long.toString(value);
        if (digits.length() > length) {
            throw new IllegalArgumentException("Value " + value + " overflows a PIC 9(" + length + ") field");
        }
        return "0".repeat(length - digits.length()) + digits;
    }

    /** Truncates or space pads {@code value} to exactly {@code length} characters. */
    public static String alphanumeric(String value, int length) {
        String text = value == null ? "" : value;
        if (text.length() >= length) {
            return text.substring(0, length);
        }
        return text + " ".repeat(length - text.length());
    }
}
