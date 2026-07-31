package com.carddemo.cbtrn02c.copybook;

import java.math.BigDecimal;

/**
 * DALYTRAN record, copybook CVTRA06Y, RECLN 350.
 *
 * <pre>
 * 05 DALYTRAN-ID             PIC X(16)      offset   0
 * 05 DALYTRAN-TYPE-CD        PIC X(02)      offset  16
 * 05 DALYTRAN-CAT-CD         PIC 9(04)      offset  18
 * 05 DALYTRAN-SOURCE         PIC X(10)      offset  22
 * 05 DALYTRAN-DESC           PIC X(100)     offset  32
 * 05 DALYTRAN-AMT            PIC S9(09)V99  offset 132
 * 05 DALYTRAN-MERCHANT-ID    PIC 9(09)      offset 143
 * 05 DALYTRAN-MERCHANT-NAME  PIC X(50)      offset 152
 * 05 DALYTRAN-MERCHANT-CITY  PIC X(50)      offset 202
 * 05 DALYTRAN-MERCHANT-ZIP   PIC X(10)      offset 252
 * 05 DALYTRAN-CARD-NUM       PIC X(16)      offset 262
 * 05 DALYTRAN-ORIG-TS        PIC X(26)      offset 278
 * 05 DALYTRAN-PROC-TS        PIC X(26)      offset 304
 * 05 FILLER                  PIC X(20)      offset 330
 * </pre>
 *
 * @param raw the record image as read, kept verbatim for the rejects file
 */
public record DalyTranRecord(
        String raw,
        String id,
        String typeCd,
        String catCd,
        String source,
        String description,
        BigDecimal amount,
        String merchantId,
        String merchantName,
        String merchantCity,
        String merchantZip,
        String cardNum,
        String origTs,
        String procTs,
        String filler) {

    public static final int LENGTH = 350;

    public static DalyTranRecord parse(String raw) {
        if (raw.length() != LENGTH) {
            throw new IllegalArgumentException("DALYTRAN record must be " + LENGTH + " bytes, got " + raw.length());
        }
        return new DalyTranRecord(
                raw,
                CobolField.alpha(raw, 0, 16),
                CobolField.alpha(raw, 16, 2),
                CobolField.digits(raw, 18, 4),
                CobolField.alpha(raw, 22, 10),
                CobolField.alpha(raw, 32, 100),
                CobolField.signed(raw, 132, 11, 2),
                CobolField.digits(raw, 143, 9),
                CobolField.alpha(raw, 152, 50),
                CobolField.alpha(raw, 202, 50),
                CobolField.alpha(raw, 252, 10),
                CobolField.alpha(raw, 262, 16),
                CobolField.alpha(raw, 278, 26),
                CobolField.alpha(raw, 304, 26),
                CobolField.alpha(raw, 330, 20));
    }
}
