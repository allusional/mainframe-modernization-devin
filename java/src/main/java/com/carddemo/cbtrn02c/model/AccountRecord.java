package com.carddemo.cbtrn02c.model;

import com.carddemo.cbtrn02c.io.FixedWidth;
import com.carddemo.cbtrn02c.io.ZonedDecimal;

import java.math.BigDecimal;

/**
 * Account master record. COBOL copybook: CVACT01Y (ACCOUNT-RECORD, 300 bytes).
 * Money fields are {@code PIC S9(10)V99}. Mutable because posting updates balances.
 */
public final class AccountRecord {

    public static final int RECORD_LENGTH = 300;
    private static final int MONEY_DIGITS = 12;
    private static final int MONEY_SCALE = 2;

    private long accountId;
    private String activeStatus;
    private BigDecimal currentBalance;
    private BigDecimal creditLimit;
    private BigDecimal cashCreditLimit;
    private String openDate;
    private String expirationDate;
    private String reissueDate;
    private BigDecimal currentCycleCredit;
    private BigDecimal currentCycleDebit;
    private String addressZip;
    private String groupId;
    private String filler;

    public static AccountRecord parse(String line) {
        String rec = FixedWidth.slice(line, 0, RECORD_LENGTH);
        AccountRecord a = new AccountRecord();
        a.accountId = Long.parseLong(FixedWidth.slice(rec, 0, 11).trim());
        a.activeStatus = FixedWidth.slice(rec, 11, 1);
        a.currentBalance = ZonedDecimal.decodeSigned(FixedWidth.slice(rec, 12, 12), MONEY_SCALE);
        a.creditLimit = ZonedDecimal.decodeSigned(FixedWidth.slice(rec, 24, 12), MONEY_SCALE);
        a.cashCreditLimit = ZonedDecimal.decodeSigned(FixedWidth.slice(rec, 36, 12), MONEY_SCALE);
        a.openDate = FixedWidth.slice(rec, 48, 10);
        a.expirationDate = FixedWidth.slice(rec, 58, 10);
        a.reissueDate = FixedWidth.slice(rec, 68, 10);
        a.currentCycleCredit = ZonedDecimal.decodeSigned(FixedWidth.slice(rec, 78, 12), MONEY_SCALE);
        a.currentCycleDebit = ZonedDecimal.decodeSigned(FixedWidth.slice(rec, 90, 12), MONEY_SCALE);
        a.addressZip = FixedWidth.slice(rec, 102, 10);
        a.groupId = FixedWidth.slice(rec, 112, 10);
        a.filler = FixedWidth.slice(rec, 122, 178);
        return a;
    }

    public String toRecord() {
        StringBuilder sb = new StringBuilder(RECORD_LENGTH);
        sb.append(FixedWidth.numeric(accountId, 11));
        sb.append(FixedWidth.alpha(activeStatus, 1));
        sb.append(ZonedDecimal.encodeSigned(currentBalance, MONEY_DIGITS, MONEY_SCALE));
        sb.append(ZonedDecimal.encodeSigned(creditLimit, MONEY_DIGITS, MONEY_SCALE));
        sb.append(ZonedDecimal.encodeSigned(cashCreditLimit, MONEY_DIGITS, MONEY_SCALE));
        sb.append(FixedWidth.alpha(openDate, 10));
        sb.append(FixedWidth.alpha(expirationDate, 10));
        sb.append(FixedWidth.alpha(reissueDate, 10));
        sb.append(ZonedDecimal.encodeSigned(currentCycleCredit, MONEY_DIGITS, MONEY_SCALE));
        sb.append(ZonedDecimal.encodeSigned(currentCycleDebit, MONEY_DIGITS, MONEY_SCALE));
        sb.append(FixedWidth.alpha(addressZip, 10));
        sb.append(FixedWidth.alpha(groupId, 10));
        sb.append(FixedWidth.alpha(filler, 178));
        return sb.toString();
    }

    public long getAccountId() {
        return accountId;
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

    public String getExpirationDate() {
        return expirationDate;
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
}
