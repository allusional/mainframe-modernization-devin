package com.aws.carddemo.cbact04c.io;

import com.aws.carddemo.cbact04c.model.AccountRecord;

/** Parses and serializes fixed-width Account master records (CVACT01Y, 300 bytes). */
public final class AccountCodec {

    public static final int RECORD_LENGTH = 300;

    private AccountCodec() {
    }

    public static AccountRecord parse(String record) {
        String r = RecordLines.fixedWidth(record, RECORD_LENGTH);
        long accountId = CobolNumber.parseUnsignedLong(r.substring(0, 11));
        String activeStatus = r.substring(11, 12);
        java.math.BigDecimal currBal = CobolNumber.parseSigned(r.substring(12, 24), 2);
        java.math.BigDecimal creditLimit = CobolNumber.parseSigned(r.substring(24, 36), 2);
        java.math.BigDecimal cashCreditLimit = CobolNumber.parseSigned(r.substring(36, 48), 2);
        String openDate = r.substring(48, 58);
        String expirationDate = r.substring(58, 68);
        String reissueDate = r.substring(68, 78);
        java.math.BigDecimal cycCredit = CobolNumber.parseSigned(r.substring(78, 90), 2);
        java.math.BigDecimal cycDebit = CobolNumber.parseSigned(r.substring(90, 102), 2);
        String addressZip = r.substring(102, 112);
        String groupId = r.substring(112, 122);
        String filler = r.substring(122, 300);
        return new AccountRecord(accountId, activeStatus, currBal, creditLimit, cashCreditLimit,
                openDate, expirationDate, reissueDate, cycCredit, cycDebit, addressZip, groupId, filler);
    }

    public static String format(AccountRecord a) {
        StringBuilder sb = new StringBuilder(RECORD_LENGTH);
        sb.append(CobolNumber.formatUnsigned(a.getAccountId(), 11));
        sb.append(Fields.padRight(a.getActiveStatus(), 1));
        sb.append(CobolNumber.formatSigned(a.getCurrentBalance(), 12, 2));
        sb.append(CobolNumber.formatSigned(a.getCreditLimit(), 12, 2));
        sb.append(CobolNumber.formatSigned(a.getCashCreditLimit(), 12, 2));
        sb.append(Fields.padRight(a.getOpenDate(), 10));
        sb.append(Fields.padRight(a.getExpirationDate(), 10));
        sb.append(Fields.padRight(a.getReissueDate(), 10));
        sb.append(CobolNumber.formatSigned(a.getCurrentCycleCredit(), 12, 2));
        sb.append(CobolNumber.formatSigned(a.getCurrentCycleDebit(), 12, 2));
        sb.append(Fields.padRight(a.getAddressZip(), 10));
        sb.append(Fields.padRight(a.getGroupId(), 10));
        sb.append(Fields.padRight(a.getFiller(), 178));
        return sb.toString();
    }
}
