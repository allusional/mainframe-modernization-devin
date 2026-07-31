package com.carddemo.cbtrn02c.copybook;

import java.math.BigDecimal;

/** {@code TRAN-CAT-BAL-RECORD} of copybook CVTRA01Y (RECLN = 50). */
public final class TranCatBalRecord {

    public static final int LENGTH = 50;
    public static final int KEY_LENGTH = 17;

    public long acctId;              // PIC 9(11)  TRANCAT-ACCT-ID
    public String typeCd = "";       // PIC X(02)  TRANCAT-TYPE-CD
    public long catCd;               // PIC 9(04)  TRANCAT-CD
    public BigDecimal balance = BigDecimal.ZERO;  // PIC S9(09)V99
    public String filler = "";       // PIC X(22)

    public static TranCatBalRecord parse(String record) {
        TranCatBalRecord r = new TranCatBalRecord();
        r.assign(record);
        return r;
    }

    /** {@code READ ... INTO}: overlays this working storage record. */
    public void assign(String record) {
        if (record.length() != LENGTH) {
            throw new IllegalArgumentException("TRAN-CAT-BAL record must be " + LENGTH + " bytes, was " + record.length());
        }
        acctId = Pic.decodeUnsigned(record.substring(0, 11));
        typeCd = record.substring(11, 13);
        catCd = Pic.decodeUnsigned(record.substring(13, 17));
        balance = Pic.decodeSigned(record.substring(17, 28), 2);
        filler = record.substring(28, 50);
    }

    /**
     * {@code INITIALIZE TRAN-CAT-BAL-RECORD}: resets the named elementary
     * items only. COBOL leaves FILLER untouched, so whatever the previous READ
     * INTO left there is carried into the next written record.
     */
    public void initialize() {
        acctId = 0;
        typeCd = "";
        catCd = 0;
        balance = BigDecimal.ZERO;
    }

    /** {@code TRAN-CAT-KEY}: account id + transaction type + transaction category. */
    public static String key(long acctId, String typeCd, long catCd) {
        return Pic.encodeUnsigned(acctId, 11) + Pic.text(typeCd, 2) + Pic.encodeUnsigned(catCd, 4);
    }

    public String key() {
        return key(acctId, typeCd, catCd);
    }

    public String toRecord() {
        return key() + Pic.encodeSigned(balance, 9, 2) + Pic.text(filler, 22);
    }
}
