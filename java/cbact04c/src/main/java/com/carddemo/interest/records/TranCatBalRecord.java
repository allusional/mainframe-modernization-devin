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
}
