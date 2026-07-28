package com.aws.carddemo.cbact04c.io;

import com.aws.carddemo.cbact04c.model.CardXrefRecord;

/** Parses fixed-width Card Cross-Reference records (CVACT03Y, 50 bytes). */
public final class CardXrefCodec {

    public static final int RECORD_LENGTH = 50;

    private CardXrefCodec() {
    }

    public static CardXrefRecord parse(String record) {
        String r = RecordLines.fixedWidth(record, RECORD_LENGTH);
        String cardNumber = r.substring(0, 16);
        long customerId = CobolNumber.parseUnsignedLong(r.substring(16, 25));
        long accountId = CobolNumber.parseUnsignedLong(r.substring(25, 36));
        return new CardXrefRecord(cardNumber, customerId, accountId);
    }
}
