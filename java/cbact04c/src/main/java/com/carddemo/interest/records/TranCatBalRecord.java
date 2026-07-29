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

    /** The trailing FILLER PIC X(22), which no program gives a meaning to. */
    public String filler() {
        return raw == null ? " ".repeat(22) : raw.substring(28, LENGTH);
    }

    /**
     * A bucket that does not exist yet, as CBTRN02C's 2700-A-CREATE-TCATBAL-REC builds it.
     *
     * <p>{@code INITIALIZE TRAN-CAT-BAL-RECORD} zeroes the balance but, per the standard,
     * leaves FILLER alone - so the 22 trailing bytes of a brand new bucket are whatever was
     * last in the record area. The caller passes that in rather than this guessing at it.
     */
    public static TranCatBalRecord initialize(long accountId, String typeCode, String categoryCode, String filler) {
        return new TranCatBalRecord(accountId, typeCode, categoryCode, BigDecimal.ZERO.setScale(2),
                " ".repeat(28) + Zoned.alphanumeric(filler, 22));
    }

    public static TranCatBalRecord initialize(long accountId, String typeCode, String categoryCode) {
        return initialize(accountId, typeCode, categoryCode, " ".repeat(22));
    }

    public String toRecord() {
        String record = Zoned.formatUnsigned(accountId, 11)
                + Zoned.alphanumeric(typeCode, 2)
                + Zoned.alphanumeric(categoryCode, 4)
                + Zoned.formatSigned(balance, 11, 2)
                + Zoned.alphanumeric(filler(), 22);
        if (record.length() != LENGTH) {
            throw new IllegalStateException("Category balance record is " + record.length()
                    + " bytes, expected " + LENGTH);
        }
        return record;
    }
}
