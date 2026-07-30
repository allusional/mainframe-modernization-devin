package com.carddemo.cbtrn02c.copybook;

/**
 * CARD-XREF record, copybook CVACT03Y, RECLN 50.
 *
 * <pre>
 * 05 XREF-CARD-NUM  PIC X(16)  offset  0
 * 05 XREF-CUST-ID   PIC 9(09)  offset 16
 * 05 XREF-ACCT-ID   PIC 9(11)  offset 25
 * 05 FILLER         PIC X(14)  offset 36
 * </pre>
 */
public record CardXrefRecord(String cardNumber, String customerId, String accountId, String filler) {

    public static final int LENGTH = 50;

    public static CardXrefRecord parse(String raw) {
        if (raw.length() != LENGTH) {
            throw new IllegalArgumentException("CARD-XREF record must be " + LENGTH + " bytes, got " + raw.length());
        }
        return new CardXrefRecord(
                CobolField.alpha(raw, 0, 16),
                CobolField.digits(raw, 16, 9),
                CobolField.digits(raw, 25, 11),
                CobolField.alpha(raw, 36, 14));
    }

    public String serialize() {
        return CobolField.moveAlpha(cardNumber, 16) + customerId + accountId + CobolField.moveAlpha(filler, 14);
    }
}
