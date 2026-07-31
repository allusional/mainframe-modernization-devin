package com.carddemo.cbtrn02c.copybook;

import java.math.BigDecimal;

/**
 * ACCOUNT record, copybook CVACT01Y, RECLN 300.
 *
 * <pre>
 * 05 ACCT-ID                 PIC 9(11)      offset   0
 * 05 ACCT-ACTIVE-STATUS      PIC X(01)      offset  11
 * 05 ACCT-CURR-BAL           PIC S9(10)V99  offset  12
 * 05 ACCT-CREDIT-LIMIT       PIC S9(10)V99  offset  24
 * 05 ACCT-CASH-CREDIT-LIMIT  PIC S9(10)V99  offset  36
 * 05 ACCT-OPEN-DATE          PIC X(10)      offset  48
 * 05 ACCT-EXPIRAION-DATE     PIC X(10)      offset  58
 * 05 ACCT-REISSUE-DATE       PIC X(10)      offset  68
 * 05 ACCT-CURR-CYC-CREDIT    PIC S9(10)V99  offset  78
 * 05 ACCT-CURR-CYC-DEBIT     PIC S9(10)V99  offset  90
 * 05 ACCT-ADDR-ZIP           PIC X(10)      offset 102
 * 05 ACCT-GROUP-ID           PIC X(10)      offset 112
 * 05 FILLER                  PIC X(178)     offset 122
 * </pre>
 *
 * <p>Mutable because CBTRN02C updates the balances in place (2800-UPDATE-ACCOUNT-REC).
 */
public final class AccountRecord {

    public static final int LENGTH = 300;
    /** Digits and scale of the S9(10)V99 money fields. */
    private static final int MONEY_LENGTH = 12;
    private static final int MONEY_SCALE = 2;

    private final String id;
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

    private AccountRecord(String id, String activeStatus, BigDecimal currentBalance, BigDecimal creditLimit,
                          BigDecimal cashCreditLimit, String openDate, String expirationDate, String reissueDate,
                          BigDecimal currentCycleCredit, BigDecimal currentCycleDebit, String addressZip,
                          String groupId, String filler) {
        this.id = id;
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

    public static AccountRecord parse(String raw) {
        if (raw.length() != LENGTH) {
            throw new IllegalArgumentException("ACCOUNT record must be " + LENGTH + " bytes, got " + raw.length());
        }
        return new AccountRecord(
                CobolField.digits(raw, 0, 11),
                CobolField.alpha(raw, 11, 1),
                CobolField.signed(raw, 12, MONEY_LENGTH, MONEY_SCALE),
                CobolField.signed(raw, 24, MONEY_LENGTH, MONEY_SCALE),
                CobolField.signed(raw, 36, MONEY_LENGTH, MONEY_SCALE),
                CobolField.alpha(raw, 48, 10),
                CobolField.alpha(raw, 58, 10),
                CobolField.alpha(raw, 68, 10),
                CobolField.signed(raw, 78, MONEY_LENGTH, MONEY_SCALE),
                CobolField.signed(raw, 90, MONEY_LENGTH, MONEY_SCALE),
                CobolField.alpha(raw, 102, 10),
                CobolField.alpha(raw, 112, 10),
                CobolField.alpha(raw, 122, 178));
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder(LENGTH);
        sb.append(id);
        sb.append(CobolField.moveAlpha(activeStatus, 1));
        sb.append(CobolField.formatSigned(currentBalance, MONEY_LENGTH, MONEY_SCALE));
        sb.append(CobolField.formatSigned(creditLimit, MONEY_LENGTH, MONEY_SCALE));
        sb.append(CobolField.formatSigned(cashCreditLimit, MONEY_LENGTH, MONEY_SCALE));
        sb.append(CobolField.moveAlpha(openDate, 10));
        sb.append(CobolField.moveAlpha(expirationDate, 10));
        sb.append(CobolField.moveAlpha(reissueDate, 10));
        sb.append(CobolField.formatSigned(currentCycleCredit, MONEY_LENGTH, MONEY_SCALE));
        sb.append(CobolField.formatSigned(currentCycleDebit, MONEY_LENGTH, MONEY_SCALE));
        sb.append(CobolField.moveAlpha(addressZip, 10));
        sb.append(CobolField.moveAlpha(groupId, 10));
        sb.append(CobolField.moveAlpha(filler, 178));
        return sb.toString();
    }

    /** ADD amount TO ACCT-CURR-BAL. */
    public void addToCurrentBalance(BigDecimal amount) {
        currentBalance = CobolField.truncate(currentBalance.add(amount), MONEY_LENGTH, MONEY_SCALE);
    }

    /** ADD amount TO ACCT-CURR-CYC-CREDIT. */
    public void addToCurrentCycleCredit(BigDecimal amount) {
        currentCycleCredit = CobolField.truncate(currentCycleCredit.add(amount), MONEY_LENGTH, MONEY_SCALE);
    }

    /** ADD amount TO ACCT-CURR-CYC-DEBIT. */
    public void addToCurrentCycleDebit(BigDecimal amount) {
        currentCycleDebit = CobolField.truncate(currentCycleDebit.add(amount), MONEY_LENGTH, MONEY_SCALE);
    }

    public String id() {
        return id;
    }

    public String activeStatus() {
        return activeStatus;
    }

    public BigDecimal currentBalance() {
        return currentBalance;
    }

    public BigDecimal creditLimit() {
        return creditLimit;
    }

    public BigDecimal cashCreditLimit() {
        return cashCreditLimit;
    }

    public String openDate() {
        return openDate;
    }

    public String expirationDate() {
        return expirationDate;
    }

    public String reissueDate() {
        return reissueDate;
    }

    public BigDecimal currentCycleCredit() {
        return currentCycleCredit;
    }

    public BigDecimal currentCycleDebit() {
        return currentCycleDebit;
    }

    public String addressZip() {
        return addressZip;
    }

    public String groupId() {
        return groupId;
    }
}
