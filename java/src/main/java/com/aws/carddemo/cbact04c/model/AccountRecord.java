package com.aws.carddemo.cbact04c.model;

import java.math.BigDecimal;

/**
 * Account master record (copybook CVACT01Y, RECLN 300).
 * <pre>
 *   ACCT-ID                PIC 9(11)
 *   ACCT-ACTIVE-STATUS     PIC X(01)
 *   ACCT-CURR-BAL          PIC S9(10)V99
 *   ACCT-CREDIT-LIMIT      PIC S9(10)V99
 *   ACCT-CASH-CREDIT-LIMIT PIC S9(10)V99
 *   ACCT-OPEN-DATE         PIC X(10)
 *   ACCT-EXPIRAION-DATE    PIC X(10)
 *   ACCT-REISSUE-DATE      PIC X(10)
 *   ACCT-CURR-CYC-CREDIT   PIC S9(10)V99
 *   ACCT-CURR-CYC-DEBIT    PIC S9(10)V99
 *   ACCT-ADDR-ZIP          PIC X(10)
 *   ACCT-GROUP-ID          PIC X(10)
 *   FILLER                 PIC X(178)
 * </pre>
 * The balance and current-cycle fields are mutable to mirror the COBOL
 * {@code REWRITE} performed by the 1050-UPDATE-ACCOUNT paragraph.
 */
public class AccountRecord {

    private final long accountId;
    private final String activeStatus;
    private BigDecimal currentBalance;
    private final BigDecimal creditLimit;
    private final BigDecimal cashCreditLimit;
    private final String openDate;
    private final String expirationDate;
    private final String reissueDate;
    private BigDecimal currentCycleCredit;
    private BigDecimal currentCycleDebit;
    private final String addressZip;
    private final String groupId;
    private final String filler;

    public AccountRecord(long accountId, String activeStatus, BigDecimal currentBalance,
                         BigDecimal creditLimit, BigDecimal cashCreditLimit, String openDate,
                         String expirationDate, String reissueDate, BigDecimal currentCycleCredit,
                         BigDecimal currentCycleDebit, String addressZip, String groupId, String filler) {
        this.accountId = accountId;
        this.activeStatus = activeStatus;
        this.currentBalance = currentBalance;
        this.creditLimit = creditLimit;
        this.cashCreditLimit = cashCreditLimit;
        this.openDate = openDate;
        this.expirationDate = expirationDate;
        this.reissueDate = reissueDate;
        this.currentCycleCredit = currentCycleCredit;
        this.currentCycleDebit = currentCycleDebit;
        this.addressZip = addressZip;
        this.groupId = groupId;
        this.filler = filler;
    }

    public long getAccountId() {
        return accountId;
    }

    public String getActiveStatus() {
        return activeStatus;
    }

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    public void setCurrentBalance(BigDecimal currentBalance) {
        this.currentBalance = currentBalance;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public BigDecimal getCashCreditLimit() {
        return cashCreditLimit;
    }

    public String getOpenDate() {
        return openDate;
    }

    public String getExpirationDate() {
        return expirationDate;
    }

    public String getReissueDate() {
        return reissueDate;
    }

    public BigDecimal getCurrentCycleCredit() {
        return currentCycleCredit;
    }

    public void setCurrentCycleCredit(BigDecimal currentCycleCredit) {
        this.currentCycleCredit = currentCycleCredit;
    }

    public BigDecimal getCurrentCycleDebit() {
        return currentCycleDebit;
    }

    public void setCurrentCycleDebit(BigDecimal currentCycleDebit) {
        this.currentCycleDebit = currentCycleDebit;
    }

    public String getAddressZip() {
        return addressZip;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getFiller() {
        return filler;
    }
}
