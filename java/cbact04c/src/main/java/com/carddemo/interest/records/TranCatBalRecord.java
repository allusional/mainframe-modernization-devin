package com.carddemo.interest.records;

import com.carddemo.interest.cobol.Zoned;

import java.math.BigDecimal;

/** Copybook CVTRA01Y - transaction category balance, 50 bytes. */
public record TranCatBalRecord(long accountId, String typeCode, String categoryCode, BigDecimal balance, String raw) {

    public static final int LENGTH = 50;

    public static TranCatBalRecord parse(String line) {
        String record = Records.pad(line, LENGTH);
        return new TranCatBalRecord(
                Zoned.parseUnsigned(record.substring(0, 11)),
                record.substring(11, 13),
                record.substring(13, 17),
                Zoned.parseSigned(record.substring(17, 28), 2),
                record);
    }

    /** The key as the COBOL program would key the disclosure group lookup with. */
    public String categoryKeySuffix() {
        return typeCode + categoryCode;
    }

    /** TRAN-CAT-KEY: account id + type code + category code, the 17 byte KSDS key. */
    public String key() {
        return Zoned.formatUnsigned(accountId, 11) + Zoned.alphanumeric(typeCode, 2)
                + Zoned.alphanumeric(categoryCode, 4);
    }

    public TranCatBalRecord withBalance(BigDecimal newBalance) {
        return new TranCatBalRecord(accountId, typeCode, categoryCode, newBalance, raw);
    }

    /**
     * A bucket that does not exist yet, as CBTRN02C's 2700-A-CREATE-TCATBAL-REC builds it:
     * INITIALIZE blanks the trailing FILLER and zeroes the balance.
     */
    public static TranCatBalRecord initialize(long accountId, String typeCode, String categoryCode) {
        return new TranCatBalRecord(accountId, typeCode, categoryCode, BigDecimal.ZERO.setScale(2), null);
    }

    public String toRecord() {
        String record = Zoned.formatUnsigned(accountId, 11)
                + Zoned.alphanumeric(typeCode, 2)
                + Zoned.alphanumeric(categoryCode, 4)
                + Zoned.formatSigned(balance, 11, 2)
                + (raw == null ? " ".repeat(22) : Zoned.alphanumeric(raw.substring(28), 22));
        if (record.length() != LENGTH) {
            throw new IllegalStateException("Category balance record is " + record.length()
                    + " bytes, expected " + LENGTH);
        }
        return record;
    }
}
