package com.carddemo.cbtrn02c.copybook;

import java.math.BigDecimal;

/**
 * TRANSACT record, copybook CVTRA05Y, RECLN 350. Field layout is identical to
 * {@link DalyTranRecord}; CBTRN02C builds it in paragraph 2000-POST-TRANSACTION.
 */
public record TranRecord(
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
        String procTs) {

    public static final int LENGTH = 350;

    /** Mirrors the MOVEs of 2000-POST-TRANSACTION. */
    public static TranRecord fromDalyTran(DalyTranRecord tran, String processingTimestamp) {
        return new TranRecord(
                tran.id(),
                tran.typeCd(),
                tran.catCd(),
                tran.source(),
                tran.description(),
                tran.amount(),
                tran.merchantId(),
                tran.merchantName(),
                tran.merchantCity(),
                tran.merchantZip(),
                tran.cardNum(),
                tran.origTs(),
                CobolField.moveAlpha(processingTimestamp, 26));
    }

    public static TranRecord parse(String raw) {
        if (raw.length() != LENGTH) {
            throw new IllegalArgumentException("TRAN record must be " + LENGTH + " bytes, got " + raw.length());
        }
        return new TranRecord(
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
                CobolField.alpha(raw, 304, 26));
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder(LENGTH);
        sb.append(CobolField.moveAlpha(id, 16));
        sb.append(CobolField.moveAlpha(typeCd, 2));
        sb.append(catCd);
        sb.append(CobolField.moveAlpha(source, 10));
        sb.append(CobolField.moveAlpha(description, 100));
        sb.append(CobolField.formatSigned(amount, 11, 2));
        sb.append(merchantId);
        sb.append(CobolField.moveAlpha(merchantName, 50));
        sb.append(CobolField.moveAlpha(merchantCity, 50));
        sb.append(CobolField.moveAlpha(merchantZip, 10));
        sb.append(CobolField.moveAlpha(cardNum, 16));
        sb.append(CobolField.moveAlpha(origTs, 26));
        sb.append(CobolField.moveAlpha(procTs, 26));
        sb.append(" ".repeat(20));
        return sb.toString();
    }
}
