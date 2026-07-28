package com.aws.carddemo.cbact04c.io;

import com.aws.carddemo.cbact04c.model.DisclosureGroupRecord;

/** Parses fixed-width Disclosure Group records (CVTRA02Y, 50 bytes). */
public final class DisclosureGroupCodec {

    public static final int RECORD_LENGTH = 50;

    private DisclosureGroupCodec() {
    }

    public static DisclosureGroupRecord parse(String record) {
        String r = RecordLines.fixedWidth(record, RECORD_LENGTH);
        String accountGroupId = r.substring(0, 10);
        String tranTypeCode = r.substring(10, 12);
        int tranCategoryCode = (int) CobolNumber.parseUnsignedLong(r.substring(12, 16));
        return new DisclosureGroupRecord(accountGroupId, tranTypeCode, tranCategoryCode,
                CobolNumber.parseSigned(r.substring(16, 22), 2));
    }
}
