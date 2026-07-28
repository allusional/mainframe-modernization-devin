package com.aws.carddemo.cbact04c.model;

import java.math.BigDecimal;

/**
 * Disclosure group record (copybook CVTRA02Y, RECLN = 50).
 * <pre>
 *   DIS-GROUP-KEY
 *     DIS-ACCT-GROUP-ID   PIC X(10)
 *     DIS-TRAN-TYPE-CD    PIC X(02)
 *     DIS-TRAN-CAT-CD     PIC 9(04)
 *   DIS-INT-RATE          PIC S9(04)V99
 *   FILLER                PIC X(28)
 * </pre>
 */
public class DisclosureGroupRecord {

    private final String accountGroupId;
    private final String tranTypeCode;
    private final int tranCategoryCode;
    private final BigDecimal interestRate;

    public DisclosureGroupRecord(String accountGroupId, String tranTypeCode,
                                 int tranCategoryCode, BigDecimal interestRate) {
        this.accountGroupId = accountGroupId;
        this.tranTypeCode = tranTypeCode;
        this.tranCategoryCode = tranCategoryCode;
        this.interestRate = interestRate;
    }

    public String getAccountGroupId() {
        return accountGroupId;
    }

    public String getTranTypeCode() {
        return tranTypeCode;
    }

    public int getTranCategoryCode() {
        return tranCategoryCode;
    }

    public BigDecimal getInterestRate() {
        return interestRate;
    }
}
