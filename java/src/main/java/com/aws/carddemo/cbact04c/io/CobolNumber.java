package com.aws.carddemo.cbact04c.io;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Encodes and decodes COBOL {@code DISPLAY} (zoned decimal) numeric fields as
 * they appear in the CardDemo ASCII sample data.
 *
 * <p>Signed fields carry the sign as an "overpunch" on the least-significant
 * (last) digit:
 * <pre>
 *   positive:  0->{  1->A  2->B  3->C  4->D  5->E  6->F  7->G  8->H  9->I
 *   negative:  0->}  1->J  2->K  3->L  4->M  5->N  6->O  7->P  8->Q  9->R
 * </pre>
 * Unsigned fields ({@code PIC 9}) are plain digit strings.
 */
public final class CobolNumber {

    private CobolNumber() {
    }

    /** Decode an unsigned zoned-decimal field to a {@link BigDecimal}. */
    public static BigDecimal parseUnsigned(String field, int scale) {
        String digits = field.trim();
        if (digits.isEmpty()) {
            digits = "0";
        }
        return new BigDecimal(new BigInteger(digits), scale);
    }

    /** Decode an unsigned zoned-decimal field to a {@code long}. */
    public static long parseUnsignedLong(String field) {
        String digits = field.trim();
        return digits.isEmpty() ? 0L : Long.parseLong(digits);
    }

    /** Decode a signed zoned-decimal (overpunched) field to a {@link BigDecimal}. */
    public static BigDecimal parseSigned(String field, int scale) {
        if (field.isEmpty()) {
            return new BigDecimal(BigInteger.ZERO, scale);
        }
        char last = field.charAt(field.length() - 1);
        StringBuilder digits = new StringBuilder(field.substring(0, field.length() - 1));
        int sign = 1;
        int lastDigit;
        switch (last) {
            case '{': lastDigit = 0; break;
            case 'A': lastDigit = 1; break;
            case 'B': lastDigit = 2; break;
            case 'C': lastDigit = 3; break;
            case 'D': lastDigit = 4; break;
            case 'E': lastDigit = 5; break;
            case 'F': lastDigit = 6; break;
            case 'G': lastDigit = 7; break;
            case 'H': lastDigit = 8; break;
            case 'I': lastDigit = 9; break;
            case '}': lastDigit = 0; sign = -1; break;
            case 'J': lastDigit = 1; sign = -1; break;
            case 'K': lastDigit = 2; sign = -1; break;
            case 'L': lastDigit = 3; sign = -1; break;
            case 'M': lastDigit = 4; sign = -1; break;
            case 'N': lastDigit = 5; sign = -1; break;
            case 'O': lastDigit = 6; sign = -1; break;
            case 'P': lastDigit = 7; sign = -1; break;
            case 'Q': lastDigit = 8; sign = -1; break;
            case 'R': lastDigit = 9; sign = -1; break;
            default:
                if (Character.isDigit(last)) {
                    lastDigit = last - '0';
                } else {
                    throw new IllegalArgumentException("Invalid zoned-decimal field: '" + field + "'");
                }
        }
        digits.append((char) ('0' + lastDigit));
        BigInteger unscaled = new BigInteger(digits.toString());
        if (sign < 0) {
            unscaled = unscaled.negate();
        }
        return new BigDecimal(unscaled, scale);
    }

    /**
     * Encode a {@link BigDecimal} as a signed zoned-decimal (overpunched) field
     * of {@code totalDigits} digits with {@code scale} implied decimal places.
     */
    public static String formatSigned(BigDecimal value, int totalDigits, int scale) {
        boolean negative = value.signum() < 0;
        BigDecimal scaled = value.abs().setScale(scale, RoundingMode.DOWN);
        String digits = scaled.unscaledValue().toString();
        if (digits.length() > totalDigits) {
            // COBOL truncates high-order digits that do not fit the receiving field.
            digits = digits.substring(digits.length() - totalDigits);
        } else {
            digits = "0".repeat(totalDigits - digits.length()) + digits;
        }
        char lastDigit = digits.charAt(digits.length() - 1);
        char overpunch = overpunch(lastDigit - '0', negative);
        return digits.substring(0, digits.length() - 1) + overpunch;
    }

    /** Encode a {@link BigDecimal} as an unsigned zoned-decimal field. */
    public static String formatUnsigned(BigDecimal value, int totalDigits, int scale) {
        BigDecimal scaled = value.abs().setScale(scale, RoundingMode.DOWN);
        String digits = scaled.unscaledValue().toString();
        if (digits.length() > totalDigits) {
            digits = digits.substring(digits.length() - totalDigits);
        } else {
            digits = "0".repeat(totalDigits - digits.length()) + digits;
        }
        return digits;
    }

    /** Encode a whole number as an unsigned zoned-decimal field of {@code totalDigits}. */
    public static String formatUnsigned(long value, int totalDigits) {
        String digits = Long.toString(Math.abs(value));
        if (digits.length() > totalDigits) {
            digits = digits.substring(digits.length() - totalDigits);
        } else {
            digits = "0".repeat(totalDigits - digits.length()) + digits;
        }
        return digits;
    }

    private static char overpunch(int digit, boolean negative) {
        if (!negative) {
            return digit == 0 ? '{' : (char) ('A' + digit - 1);
        }
        return digit == 0 ? '}' : (char) ('J' + digit - 1);
    }
}
