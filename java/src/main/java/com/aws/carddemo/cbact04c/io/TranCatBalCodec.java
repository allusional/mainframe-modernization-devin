package com.aws.carddemo.cbact04c.io;

import com.aws.carddemo.cbact04c.model.TranCatBalRecord;

/** Parses fixed-width Transaction Category Balance records (CVTRA01Y, 50 bytes). */
public final class TranCatBalCodec {

    public static final int RECORD_LENGTH = 50;

    private TranCatBalCodec() {
    }

    public static TranCatBalRecord parse(String record) {
        String r = RecordLines.fixedWidth(record, RECORD_LENGTH);
        long accountId = CobolNumber.parseUnsignedLong(r.substring(0, 11));
        String typeCode = r.substring(11, 13);
        int categoryCode = (int) CobolNumber.parseUnsignedLong(r.substring(13, 17));
        return new TranCatBalRecord(accountId, typeCode, categoryCode,
                CobolNumber.parseSigned(r.substring(17, 28), 2));
    }
}
