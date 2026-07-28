package com.carddemo.cbtrn02c.io;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Encodes and decodes COBOL zoned-decimal (DISPLAY) numeric fields as they
 * appear in the CardDemo ASCII sample datasets.
 *
 * <p>Signed fields ({@code PIC S9(n)V99}) carry the sign in the last byte using
 * the classic overpunch convention that survives the mainframe EBCDIC-to-ASCII
 * conversion used to produce {@code app/data/ASCII}:
 * <ul>
 *     <li>positive digits 0-9 map to {@code { A B C D E F G H I}</li>
 *     <li>negative digits 0-9 map to {@code } J K L M N O P Q R}</li>
 * </ul>
 * The {@code V99} in a PIC clause is an implied (non-stored) decimal point, so
 * the raw digits are scaled by 100.
 */
public final class ZonedDecimal {

    private static final String POSITIVE = "{ABCDEFGHI";
    private static final String NEGATIVE = "}JKLMNOPQR";

    private ZonedDecimal() {
    }

    /**
     * Decodes a signed zoned-decimal field with {@code scale} implied decimals.
     */
    public static BigDecimal decodeSigned(String raw, int scale) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return BigDecimal.ZERO.setScale(scale);
        }
        char last = trimmed.charAt(trimmed.length() - 1);
        String leading = trimmed.substring(0, trimmed.length() - 1);
        boolean negative = false;
        int lastDigit;
        int posIdx = POSITIVE.indexOf(last);
        int negIdx = NEGATIVE.indexOf(last);
        if (posIdx >= 0) {
            lastDigit = posIdx;
        } else if (negIdx >= 0) {
            lastDigit = negIdx;
            negative = true;
        } else if (Character.isDigit(last)) {
            lastDigit = last - '0';
        } else {
            throw new IllegalArgumentException("Invalid overpunch character '" + last + "' in [" + raw + "]");
        }
        String digits = leading + lastDigit;
        BigDecimal unscaled = new BigDecimal(digits.isEmpty() ? "0" : digits);
        BigDecimal value = unscaled.movePointLeft(scale);
        return negative ? value.negate() : value;
    }

    /**
     * Encodes a value back into a signed zoned-decimal field of {@code totalDigits}
     * digits (integer + implied decimals) using overpunch on the final byte.
     * High-order digits beyond {@code totalDigits} are truncated, mirroring the
     * COBOL behaviour of moving a value into a fixed-size numeric field.
     */
    public static String encodeSigned(BigDecimal value, int totalDigits, int scale) {
        boolean negative = value.signum() < 0;
        BigDecimal abs = value.abs().setScale(scale, RoundingMode.DOWN);
        String digits = abs.movePointRight(scale).toBigInteger().toString();
        if (digits.length() > totalDigits) {
            digits = digits.substring(digits.length() - totalDigits);
        } else {
            digits = "0".repeat(totalDigits - digits.length()) + digits;
        }
        int lastDigit = digits.charAt(digits.length() - 1) - '0';
        char overpunch = (negative ? NEGATIVE : POSITIVE).charAt(lastDigit);
        return digits.substring(0, digits.length() - 1) + overpunch;
    }

    /**
     * Decodes an unsigned zoned-decimal field (plain digits) with implied decimals.
     */
    public static BigDecimal decodeUnsigned(String raw, int scale) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return BigDecimal.ZERO.setScale(scale);
        }
        return new BigDecimal(trimmed).movePointLeft(scale);
    }
}
