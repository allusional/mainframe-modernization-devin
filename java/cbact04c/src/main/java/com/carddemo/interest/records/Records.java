package com.carddemo.interest.records;

/** Shared helpers for fixed width record parsing. */
final class Records {

    private Records() {
    }

    /** Right pads a (possibly short) input line to the record length. */
    static String pad(String line, int length) {
        String text = line == null ? "" : line;
        if (text.length() >= length) {
            return text;
        }
        return text + " ".repeat(length - text.length());
    }
}
