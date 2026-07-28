package com.carddemo.interest.records;

import com.carddemo.interest.cobol.Zoned;

/** Copybook CVACT03Y - card / customer / account cross reference, 50 bytes. */
public record CardXrefRecord(String cardNumber, long customerId, long accountId) {

    public static final int LENGTH = 50;

    public static CardXrefRecord parse(String line) {
        String record = Records.pad(line, LENGTH);
        return new CardXrefRecord(
                record.substring(0, 16),
                Zoned.parseUnsigned(record.substring(16, 25)),
                Zoned.parseUnsigned(record.substring(25, 36)));
    }
}
