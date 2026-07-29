package com.carddemo.posting.records;

import com.carddemo.interest.cobol.Zoned;
import com.carddemo.posting.rules.RejectReason;

/**
 * The DALYREJS record, declared inline in CBTRN02C rather than in a copybook
 * ({@code CBTRN02C.cbl:81-84}): the 350 byte input record verbatim, followed by an 80 byte
 * trailer of {@code WS-VALIDATION-FAIL-REASON PIC 9(04)} and
 * {@code WS-VALIDATION-FAIL-REASON-DESC PIC X(76)}.
 *
 * <p>350 + 80 = 430, which is the {@code LRECL=430} on the DALYREJS DD in POSTTRAN.jcl.
 */
public record RejectRecord(String dailyTransactionRecord, RejectReason reason) {

    public static final int LENGTH = 430;
    public static final int TRAILER_LENGTH = 80;

    public String toRecord() {
        String record = Zoned.alphanumeric(dailyTransactionRecord, DailyTransactionRecord.LENGTH)
                + Zoned.formatUnsigned(reason.code(), 4)
                + Zoned.alphanumeric(reason.description(), TRAILER_LENGTH - 4);
        if (record.length() != LENGTH) {
            throw new IllegalStateException("Reject record is " + record.length() + " bytes, expected " + LENGTH);
        }
        return record;
    }
}
