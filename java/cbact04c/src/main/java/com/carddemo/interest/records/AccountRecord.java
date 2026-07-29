package com.carddemo.interest.records;

import com.carddemo.interest.cobol.Zoned;

import java.math.BigDecimal;

/**
 * Copybook CVACT01Y - account master, 300 bytes. Mutable, because both CBACT04C and
 * CBTRN02C rewrite the balance and the cycle to date totals in place.
 */
public final class AccountRecord {

    public static final int LENGTH = 300;

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

    private AccountRecord(long accountId, String activeStatus, BigDecimal currentBalance, BigDecimal creditLimit,
                          BigDecimal cashCreditLimit, String openDate, String expirationDate, String reissueDate,
                          BigDecimal currentCycleCredit, BigDecimal currentCycleDebit, String addressZip,
                          String groupId, String filler) {
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

    public static AccountRecord parse(String line) {
        String record = Records.pad(line, LENGTH);
        return new AccountRecord(
                Zoned.parseUnsigned(record.substring(0, 11)),
                record.substring(11, 12),
                Zoned.parseSigned(record.substring(12, 24), 2),
                Zoned.parseSigned(record.substring(24, 36), 2),
                Zoned.parseSigned(record.substring(36, 48), 2),
                record.substring(48, 58),
                record.substring(58, 68),
                record.substring(68, 78),
                Zoned.parseSigned(record.substring(78, 90), 2),
                Zoned.parseSigned(record.substring(90, 102), 2),
                record.substring(102, 112),
                record.substring(112, 122),
                record.substring(122, LENGTH));
    }

    public String toRecord() {
        return Zoned.formatUnsigned(accountId, 11)
                + Zoned.alphanumeric(activeStatus, 1)
                + Zoned.formatSigned(currentBalance, 12, 2)
                + Zoned.formatSigned(creditLimit, 12, 2)
                + Zoned.formatSigned(cashCreditLimit, 12, 2)
                + Zoned.alphanumeric(openDate, 10)
                + Zoned.alphanumeric(expirationDate, 10)
                + Zoned.alphanumeric(reissueDate, 10)
                + Zoned.formatSigned(currentCycleCredit, 12, 2)
                + Zoned.formatSigned(currentCycleDebit, 12, 2)
                + Zoned.alphanumeric(addressZip, 10)
                + Zoned.alphanumeric(groupId, 10)
                + Zoned.alphanumeric(filler, LENGTH - 122);
    }

    public long accountId() {
        return accountId;
    }

    public String activeStatus() {
        return activeStatus;
    }

    public String groupId() {
        return groupId;
    }

    public BigDecimal currentBalance() {
        return currentBalance;
    }

    public BigDecimal creditLimit() {
        return creditLimit;
    }

    /** ACCT-EXPIRAION-DATE, PIC X(10). The copybook's spelling, kept deliberately. */
    public String expirationDate() {
        return expirationDate;
    }

    public BigDecimal currentCycleCredit() {
        return currentCycleCredit;
    }

    public BigDecimal currentCycleDebit() {
        return currentCycleDebit;
    }

    public void addToCurrentBalance(BigDecimal amount) {
        currentBalance = currentBalance.add(amount);
    }

    public void addToCurrentCycleCredit(BigDecimal amount) {
        currentCycleCredit = currentCycleCredit.add(amount);
    }

    public void addToCurrentCycleDebit(BigDecimal amount) {
        currentCycleDebit = currentCycleDebit.add(amount);
    }

    /** 1050-UPDATE-ACCOUNT: post the cycle's interest and close the cycle out. */
    public void applyInterestAndCloseCycle(BigDecimal totalInterest) {
        currentBalance = currentBalance.add(totalInterest);
        currentCycleCredit = BigDecimal.ZERO.setScale(2);
        currentCycleDebit = BigDecimal.ZERO.setScale(2);
    }
}
