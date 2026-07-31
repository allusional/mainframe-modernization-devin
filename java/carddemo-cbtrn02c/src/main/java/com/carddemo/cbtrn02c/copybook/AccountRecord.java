package com.carddemo.cbtrn02c.copybook;

import java.math.BigDecimal;

/** {@code ACCOUNT-RECORD} of copybook CVACT01Y (RECLN = 300). */
public final class AccountRecord {

    public static final int LENGTH = 300;

    public long acctId;                    // PIC 9(11)
    public String activeStatus;            // PIC X(01)
    public BigDecimal currBal;             // PIC S9(10)V99
    public BigDecimal creditLimit;         // PIC S9(10)V99
    public BigDecimal cashCreditLimit;     // PIC S9(10)V99
    public String openDate;                // PIC X(10)
    public String expirationDate;          // PIC X(10)
    public String reissueDate;             // PIC X(10)
    public BigDecimal currCycCredit;       // PIC S9(10)V99
    public BigDecimal currCycDebit;        // PIC S9(10)V99
    public String addrZip;                 // PIC X(10)
    public String groupId;                 // PIC X(10)
    public String filler;                  // PIC X(178)

    public static AccountRecord parse(String record) {
        if (record.length() != LENGTH) {
            throw new IllegalArgumentException("ACCOUNT record must be " + LENGTH + " bytes, was " + record.length());
        }
        AccountRecord r = new AccountRecord();
        r.acctId = Pic.decodeUnsigned(record.substring(0, 11));
        r.activeStatus = record.substring(11, 12);
        r.currBal = Pic.decodeSigned(record.substring(12, 24), 2);
        r.creditLimit = Pic.decodeSigned(record.substring(24, 36), 2);
        r.cashCreditLimit = Pic.decodeSigned(record.substring(36, 48), 2);
        r.openDate = record.substring(48, 58);
        r.expirationDate = record.substring(58, 68);
        r.reissueDate = record.substring(68, 78);
        r.currCycCredit = Pic.decodeSigned(record.substring(78, 90), 2);
        r.currCycDebit = Pic.decodeSigned(record.substring(90, 102), 2);
        r.addrZip = record.substring(102, 112);
        r.groupId = record.substring(112, 122);
        r.filler = record.substring(122, 300);
        return r;
    }

    public String toRecord() {
        return Pic.encodeUnsigned(acctId, 11)
                + Pic.text(activeStatus, 1)
                + Pic.encodeSigned(currBal, 10, 2)
                + Pic.encodeSigned(creditLimit, 10, 2)
                + Pic.encodeSigned(cashCreditLimit, 10, 2)
                + Pic.text(openDate, 10)
                + Pic.text(expirationDate, 10)
                + Pic.text(reissueDate, 10)
                + Pic.encodeSigned(currCycCredit, 10, 2)
                + Pic.encodeSigned(currCycDebit, 10, 2)
                + Pic.text(addrZip, 10)
                + Pic.text(groupId, 10)
                + Pic.text(filler, 178);
    }
}
