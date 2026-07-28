package com.carddemo.interest.records;

import com.carddemo.interest.cobol.Zoned;

import java.math.BigDecimal;

/** Copybook CVTRA02Y - disclosure group (rate card), 50 bytes. */
public record DisclosureGroupRecord(String accountGroupId, String typeCode, String categoryCode,
                                    BigDecimal annualInterestRate) {

    public static final int LENGTH = 50;

    public static DisclosureGroupRecord parse(String line) {
        String record = Records.pad(line, LENGTH);
        return new DisclosureGroupRecord(
                record.substring(0, 10),
                record.substring(10, 12),
                record.substring(12, 16),
                Zoned.parseSigned(record.substring(16, 22), 2));
    }

    public String key() {
        return key(accountGroupId, typeCode, categoryCode);
    }

    public static String key(String accountGroupId, String typeCode, String categoryCode) {
        return Zoned.alphanumeric(accountGroupId, 10) + typeCode + categoryCode;
    }
}
