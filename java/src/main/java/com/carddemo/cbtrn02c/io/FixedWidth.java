package com.carddemo.cbtrn02c.io;

/**
 * Small helpers for slicing and building fixed-width COBOL records.
 */
public final class FixedWidth {

    private FixedWidth() {
    }

    /**
     * Extracts the {@code length} characters starting at {@code offset}. The
     * record is right-padded with spaces first so short (trailing-filler-trimmed)
     * lines from the sample data behave like fixed-length mainframe records.
     */
    public static String slice(String record, int offset, int length) {
        String padded = record;
        int needed = offset + length;
        if (padded.length() < needed) {
            padded = padded + " ".repeat(needed - padded.length());
        }
        return padded.substring(offset, offset + length);
    }

    /** Left-justified alphanumeric field, space padded / truncated to {@code length}. */
    public static String alpha(String value, int length) {
        String v = value == null ? "" : value;
        if (v.length() > length) {
            return v.substring(0, length);
        }
        return v + " ".repeat(length - v.length());
    }

    /** Right-justified, zero-filled unsigned numeric field of {@code length} digits. */
    public static String numeric(long value, int length) {
        String v = Long.toString(value);
        if (v.length() > length) {
            v = v.substring(v.length() - length);
        }
        return "0".repeat(length - v.length()) + v;
    }
}
