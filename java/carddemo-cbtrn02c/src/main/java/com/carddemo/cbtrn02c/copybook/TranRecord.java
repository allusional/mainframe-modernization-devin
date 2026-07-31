package com.carddemo.cbtrn02c.copybook;

import java.math.BigDecimal;

/** {@code TRAN-RECORD} of copybook CVTRA05Y (RECLN = 350). */
public final class TranRecord {

    public static final int LENGTH = 350;

    public String id = "";             // PIC X(16)
    public String typeCd = "";         // PIC X(02)
    public long catCd;                 // PIC 9(04)
    public String source = "";         // PIC X(10)
    public String desc = "";           // PIC X(100)
    public BigDecimal amt = BigDecimal.ZERO;  // PIC S9(09)V99
    public long merchantId;            // PIC 9(09)
    public String merchantName = "";   // PIC X(50)
    public String merchantCity = "";   // PIC X(50)
    public String merchantZip = "";    // PIC X(10)
    public String cardNum = "";        // PIC X(16)
    public String origTs = "";         // PIC X(26)
    public String procTs = "";         // PIC X(26)
    /** WORKING-STORAGE FILLER: spaces, never populated by CBTRN02C. */
    public String filler = "";         // PIC X(20)

    public static TranRecord parse(String record) {
        if (record.length() != LENGTH) {
            throw new IllegalArgumentException("TRAN record must be " + LENGTH + " bytes, was " + record.length());
        }
        TranRecord r = new TranRecord();
        r.id = record.substring(0, 16);
        r.typeCd = record.substring(16, 18);
        r.catCd = Pic.decodeUnsigned(record.substring(18, 22));
        r.source = record.substring(22, 32);
        r.desc = record.substring(32, 132);
        r.amt = Pic.decodeSigned(record.substring(132, 143), 2);
        r.merchantId = Pic.decodeUnsigned(record.substring(143, 152));
        r.merchantName = record.substring(152, 202);
        r.merchantCity = record.substring(202, 252);
        r.merchantZip = record.substring(252, 262);
        r.cardNum = record.substring(262, 278);
        r.origTs = record.substring(278, 304);
        r.procTs = record.substring(304, 330);
        r.filler = record.substring(330, 350);
        return r;
    }

    public String toRecord() {
        return Pic.text(id, 16)
                + Pic.text(typeCd, 2)
                + Pic.encodeUnsigned(catCd, 4)
                + Pic.text(source, 10)
                + Pic.text(desc, 100)
                + Pic.encodeSigned(amt, 9, 2)
                + Pic.encodeUnsigned(merchantId, 9)
                + Pic.text(merchantName, 50)
                + Pic.text(merchantCity, 50)
                + Pic.text(merchantZip, 10)
                + Pic.text(cardNum, 16)
                + Pic.text(origTs, 26)
                + Pic.text(procTs, 26)
                + Pic.text(filler, 20);
    }
}
