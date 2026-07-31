package com.carddemo.cbtrn02c.copybook;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Encoding and decoding of COBOL {@code USAGE DISPLAY} picture clauses as used
 * by the CardDemo copybooks.
 *
 * <p>Signed fields ({@code PIC S9(n)V99}) carry an IBM overpunched trailing
 * sign: the last digit is replaced by {@code {} and {@code A}-{@code I} for
 * +0..+9 and by {@code }} and {@code J}-{@code R} for -0..-9. Unsigned fields
 * ({@code PIC 9(n)}) are plain zero-padded digits.
 */
public final class Pic {

    private static final String POSITIVE_OVERPUNCH = "{ABCDEFGHI";
    private static final String NEGATIVE_OVERPUNCH = "}JKLMNOPQR";

    private Pic() {
    }

    /** Decodes {@code PIC S9(intDigits)V9(decDigits)} DISPLAY into a scaled BigDecimal. */
    public static BigDecimal decodeSigned(String field, int decDigits) {
        String digits = field.trim();
        if (digits.isEmpty()) {
            return BigDecimal.ZERO.setScale(decDigits);
        }
        char last = digits.charAt(digits.length() - 1);
        String head = digits.substring(0, digits.length() - 1);
        boolean negative = false;
        char lastDigit;
        int positive = POSITIVE_OVERPUNCH.indexOf(last);
        int negativeIdx = NEGATIVE_OVERPUNCH.indexOf(last);
        if (positive >= 0) {
            lastDigit = (char) ('0' + positive);
        } else if (negativeIdx >= 0) {
            lastDigit = (char) ('0' + negativeIdx);
            negative = true;
        } else if (Character.isDigit(last)) {
            // Unpunched trailing digit: the host treats zone F as positive.
            lastDigit = last;
        } else {
            throw new IllegalArgumentException("Not a signed DISPLAY field: '" + field + "'");
        }
        BigDecimal value = new BigDecimal(new java.math.BigInteger(head + lastDigit), decDigits);
        return negative ? value.negate() : value;
    }

    /** Encodes a BigDecimal as {@code PIC S9(intDigits)V9(decDigits)} DISPLAY. */
    public static String encodeSigned(BigDecimal value, int intDigits, int decDigits) {
        BigDecimal scaled = value.setScale(decDigits, RoundingMode.DOWN);
        String digits = scaled.abs().unscaledValue().toString();
        int width = intDigits + decDigits;
        if (digits.length() > width) {
            // COBOL truncates high-order digits that do not fit the picture.
            digits = digits.substring(digits.length() - width);
        }
        digits = "0".repeat(width - digits.length()) + digits;
        int lastDigit = digits.charAt(width - 1) - '0';
        String table = scaled.signum() < 0 ? NEGATIVE_OVERPUNCH : POSITIVE_OVERPUNCH;
        return digits.substring(0, width - 1) + table.charAt(lastDigit);
    }

    /** Decodes {@code PIC 9(n)} DISPLAY. Blank fields decode to zero. */
    public static long decodeUnsigned(String field) {
        String digits = field.trim();
        if (digits.isEmpty()) {
            return 0L;
        }
        return Long.parseLong(digits);
    }

    /** Encodes {@code PIC 9(n)} DISPLAY. */
    public static String encodeUnsigned(long value, int digits) {
        String s = Long.toString(Math.abs(value));
        if (s.length() > digits) {
            s = s.substring(s.length() - digits);
        }
        return "0".repeat(digits - s.length()) + s;
    }

    /** Left justifies and space pads (or truncates) an alphanumeric field. */
    public static String text(String value, int width) {
        String s = value == null ? "" : value;
        if (s.length() >= width) {
            return s.substring(0, width);
        }
        return s + " ".repeat(width - s.length());
    }
}
