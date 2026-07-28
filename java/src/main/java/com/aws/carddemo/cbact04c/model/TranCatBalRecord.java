package com.aws.carddemo.cbact04c.model;

import java.math.BigDecimal;

/**
 * Transaction Category Balance record (copybook CVTRA01Y, RECLN = 50).
 * <pre>
 *   TRAN-CAT-KEY
 *     TRANCAT-ACCT-ID   PIC 9(11)
 *     TRANCAT-TYPE-CD   PIC X(02)
 *     TRANCAT-CD        PIC 9(04)
 *   TRAN-CAT-BAL        PIC S9(09)V99
 *   FILLER              PIC X(22)
 * </pre>
 */
public class TranCatBalRecord {

    private final long accountId;
    private final String typeCode;
    private final int categoryCode;
    private final BigDecimal balance;

    public TranCatBalRecord(long accountId, String typeCode, int categoryCode, BigDecimal balance) {
        this.accountId = accountId;
        this.typeCode = typeCode;
        this.categoryCode = categoryCode;
        this.balance = balance;
    }

    public long getAccountId() {
        return accountId;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public int getCategoryCode() {
        return categoryCode;
    }

    public BigDecimal getBalance() {
        return balance;
    }
}
