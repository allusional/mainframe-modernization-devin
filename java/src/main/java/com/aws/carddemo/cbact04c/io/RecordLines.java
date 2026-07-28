package com.aws.carddemo.cbact04c.io;

/** Utilities for normalizing fixed-width record text read from ASCII sample files. */
final class RecordLines {

    private RecordLines() {
    }

    /**
     * Strip a trailing CR/LF and pad the record with spaces so it is at least
     * {@code length} characters wide, mirroring a fixed-length COBOL record.
     */
    static String fixedWidth(String record, int length) {
        String r = record;
        while (!r.isEmpty()) {
            char last = r.charAt(r.length() - 1);
            if (last == '\r' || last == '\n') {
                r = r.substring(0, r.length() - 1);
            } else {
                break;
            }
        }
        if (r.length() < length) {
            r = r + " ".repeat(length - r.length());
        }
        return r;
    }
}
