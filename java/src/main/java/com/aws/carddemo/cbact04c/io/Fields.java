package com.aws.carddemo.cbact04c.io;

/** Small helpers for fixed-width alphanumeric COBOL fields. */
final class Fields {

    private Fields() {
    }

    /** Right-pad (or truncate) to width {@code n} with spaces, mirroring a COBOL X(n) field. */
    static String padRight(String value, int n) {
        String v = value == null ? "" : value;
        if (v.length() >= n) {
            return v.substring(0, n);
        }
        return v + " ".repeat(n - v.length());
    }
}
