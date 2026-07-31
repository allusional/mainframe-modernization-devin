package com.carddemo.cbtrn02c.copybook;

import java.math.BigDecimal;

/**
 * TRAN-CAT-BAL record, copybook CVTRA01Y, RECLN 50.
 *
 * <pre>
 * 05 TRAN-CAT-KEY
 *    10 TRANCAT-ACCT-ID  PIC 9(11)      offset  0
 *    10 TRANCAT-TYPE-CD  PIC X(02)      offset 11
 *    10 TRANCAT-CD       PIC 9(04)      offset 13
 * 05 TRAN-CAT-BAL        PIC S9(09)V99  offset 17
 * 05 FILLER              PIC X(22)      offset 28
 * </pre>
 *
 * <p>Mutable because CBTRN02C accumulates into TRAN-CAT-BAL (2700-UPDATE-TCATBAL).
 */
public final class TranCatBalRecord {

    public static final int LENGTH = 50;
    private static final int BALANCE_LENGTH = 11;
    private static final int BALANCE_SCALE = 2;

    private final String accountId;
    private final String typeCode;
    private final String categoryCode;
    private BigDecimal balance;
    private final String filler;

    private TranCatBalRecord(String accountId, String typeCode, String categoryCode, BigDecimal balance,
                             String filler) {
        this.accountId = accountId;
        this.typeCode = typeCode;
        this.categoryCode = categoryCode;
        this.balance = balance;
        this.filler = filler;
    }

    public static TranCatBalRecord parse(String raw) {
        if (raw.length() != LENGTH) {
            throw new IllegalArgumentException("TRAN-CAT-BAL record must be " + LENGTH + " bytes, got " + raw.length());
        }
        return new TranCatBalRecord(
                CobolField.digits(raw, 0, 11),
                CobolField.alpha(raw, 11, 2),
                CobolField.digits(raw, 13, 4),
                CobolField.signed(raw, 17, BALANCE_LENGTH, BALANCE_SCALE),
                CobolField.alpha(raw, 28, 22));
    }

    /**
     * Mirrors 2700-A-CREATE-TCATBAL-REC. INITIALIZE zeroes TRAN-CAT-BAL but, per the COBOL
     * standard, leaves FILLER items untouched, so the new record inherits the FILLER bytes that
     * are still in the TRAN-CAT-BAL-RECORD working storage area from the last record read.
     */
    public static TranCatBalRecord create(String accountId, String typeCode, String categoryCode,
                                          String recordAreaFiller) {
        return new TranCatBalRecord(accountId, typeCode, categoryCode, BigDecimal.ZERO.setScale(BALANCE_SCALE),
                CobolField.moveAlpha(recordAreaFiller, 22));
    }

    public String serialize() {
        return accountId
                + CobolField.moveAlpha(typeCode, 2)
                + categoryCode
                + CobolField.formatSigned(balance, BALANCE_LENGTH, BALANCE_SCALE)
                + CobolField.moveAlpha(filler, 22);
    }

    /** ADD amount TO TRAN-CAT-BAL. */
    public void addToBalance(BigDecimal amount) {
        balance = CobolField.truncate(balance.add(amount), BALANCE_LENGTH, BALANCE_SCALE);
    }

    /** The 17 byte TRAN-CAT-KEY. */
    public String key() {
        return accountId + typeCode + categoryCode;
    }

    public String accountId() {
        return accountId;
    }

    public String typeCode() {
        return typeCode;
    }

    public String categoryCode() {
        return categoryCode;
    }

    public BigDecimal balance() {
        return balance;
    }

    /** The trailing FILLER bytes of the record. */
    public String filler() {
        return filler;
    }
}
