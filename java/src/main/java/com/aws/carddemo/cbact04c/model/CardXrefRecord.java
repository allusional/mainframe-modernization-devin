package com.aws.carddemo.cbact04c.model;

/**
 * Card cross-reference record (copybook CVACT03Y, RECLN 50).
 * <pre>
 *   XREF-CARD-NUM   PIC X(16)
 *   XREF-CUST-ID    PIC 9(09)
 *   XREF-ACCT-ID    PIC 9(11)
 *   FILLER          PIC X(14)
 * </pre>
 */
public class CardXrefRecord {

    private final String cardNumber;
    private final long customerId;
    private final long accountId;

    public CardXrefRecord(String cardNumber, long customerId, long accountId) {
        this.cardNumber = cardNumber;
        this.customerId = customerId;
        this.accountId = accountId;
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
