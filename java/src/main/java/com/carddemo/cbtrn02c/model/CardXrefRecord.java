package com.carddemo.cbtrn02c.model;

import com.carddemo.cbtrn02c.io.FixedWidth;

/**
 * Card cross-reference record. COBOL copybook: CVACT03Y (CARD-XREF-RECORD, 50 bytes;
 * trailing filler is trimmed in the ASCII sample data).
 */
public final class CardXrefRecord {

    private final String cardNumber;
    private final long customerId;
    private final long accountId;

    public CardXrefRecord(String cardNumber, long customerId, long accountId) {
        this.cardNumber = cardNumber;
        this.customerId = customerId;
        this.accountId = accountId;
    }

    public static CardXrefRecord parse(String line) {
        String rec = FixedWidth.slice(line, 0, 36);
        return new CardXrefRecord(
                FixedWidth.slice(rec, 0, 16),
                Long.parseLong(FixedWidth.slice(rec, 16, 9).trim()),
                Long.parseLong(FixedWidth.slice(rec, 25, 11).trim()));
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public long getCustomerId() {
        return customerId;
    }

    public long getAccountId() {
        return accountId;
    }
}
