package com.carddemo.cbtrn02c.model;

import com.carddemo.cbtrn02c.io.FixedWidth;
import com.carddemo.cbtrn02c.io.ZonedDecimal;

import java.math.BigDecimal;

/**
 * Transaction category balance record. COBOL copybook: CVTRA01Y
 * (TRAN-CAT-BAL-RECORD, 50 bytes). Key = account id + type code + category code.
 */
public final class TranCatBalRecord {

    public static final int RECORD_LENGTH = 50;
    private static final int BAL_DIGITS = 11;
    private static final int BAL_SCALE = 2;

    private long accountId;
    private String typeCode;
    private String categoryCode;
    private BigDecimal balance;
    private String filler;

    /** Builds the composite key exactly as FD-TRAN-CAT-KEY is assembled in COBOL. */
    public static String key(long accountId, String typeCode, String categoryCode) {
        return FixedWidth.numeric(accountId, 11)
                + FixedWidth.alpha(typeCode, 2)
                + FixedWidth.numeric(Long.parseLong(categoryCode.trim()), 4);
    }

    public static TranCatBalRecord parse(String line) {
        String rec = FixedWidth.slice(line, 0, RECORD_LENGTH);
        TranCatBalRecord t = new TranCatBalRecord();
        t.accountId = Long.parseLong(FixedWidth.slice(rec, 0, 11).trim());
        t.typeCode = FixedWidth.slice(rec, 11, 2);
        t.categoryCode = FixedWidth.slice(rec, 13, 4);
        t.balance = ZonedDecimal.decodeSigned(FixedWidth.slice(rec, 17, 11), BAL_SCALE);
        t.filler = FixedWidth.slice(rec, 28, 22);
        return t;
    }

    /**
     * Creates a fresh category balance record (paragraph 2700-A-CREATE-TCATBAL-REC).
     * INITIALIZE sets the balance to zero and the filler to zeros/spaces before the key
     * fields are moved in.
     */
    public static TranCatBalRecord create(long accountId, String typeCode, String categoryCode) {
        TranCatBalRecord t = new TranCatBalRecord();
        t.accountId = accountId;
        t.typeCode = typeCode;
        t.categoryCode = categoryCode;
        t.balance = BigDecimal.ZERO.setScale(BAL_SCALE);
        t.filler = "0".repeat(22);
        return t;
    }

    public String toRecord() {
        return FixedWidth.numeric(accountId, 11)
                + FixedWidth.alpha(typeCode, 2)
                + FixedWidth.numeric(Long.parseLong(categoryCode.trim()), 4)
                + ZonedDecimal.encodeSigned(balance, BAL_DIGITS, BAL_SCALE)
                + FixedWidth.alpha(filler, 22);
    }

    public String getKey() {
        return key(accountId, typeCode, categoryCode);
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
}
