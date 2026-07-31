package com.carddemo.cbtrn02c.copybook;

/** {@code CARD-XREF-RECORD} of copybook CVACT03Y (RECLN = 50). */
public final class CardXrefRecord {

    public static final int LENGTH = 50;

    public String cardNum;   // PIC X(16)
    public long custId;      // PIC 9(09)
    public long acctId;      // PIC 9(11)
    public String filler;    // PIC X(14)

    public static CardXrefRecord parse(String record) {
        if (record.length() != LENGTH) {
            throw new IllegalArgumentException("CARD-XREF record must be " + LENGTH + " bytes, was " + record.length());
        }
        CardXrefRecord r = new CardXrefRecord();
        r.cardNum = record.substring(0, 16);
        r.custId = Pic.decodeUnsigned(record.substring(16, 25));
        r.acctId = Pic.decodeUnsigned(record.substring(25, 36));
        r.filler = record.substring(36, 50);
        return r;
    }

    public String toRecord() {
        return Pic.text(cardNum, 16)
                + Pic.encodeUnsigned(custId, 9)
                + Pic.encodeUnsigned(acctId, 11)
                + Pic.text(filler, 14);
    }
}
