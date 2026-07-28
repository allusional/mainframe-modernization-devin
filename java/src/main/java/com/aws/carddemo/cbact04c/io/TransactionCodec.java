package com.aws.carddemo.cbact04c.io;

import com.aws.carddemo.cbact04c.model.TransactionRecord;

/** Serializes fixed-width Transaction records (CVTRA05Y, 350 bytes). */
public final class TransactionCodec {

    public static final int RECORD_LENGTH = 350;

    private TransactionCodec() {
    }

    public static String format(TransactionRecord t) {
        StringBuilder sb = new StringBuilder(RECORD_LENGTH);
        sb.append(Fields.padRight(t.getId(), 16));
        sb.append(Fields.padRight(t.getTypeCode(), 2));
        sb.append(CobolNumber.formatUnsigned(t.getCategoryCode(), 4));
        sb.append(Fields.padRight(t.getSource(), 10));
        sb.append(Fields.padRight(t.getDescription(), 100));
        sb.append(CobolNumber.formatSigned(t.getAmount(), 11, 2));
        sb.append(CobolNumber.formatUnsigned(t.getMerchantId(), 9));
        sb.append(Fields.padRight(t.getMerchantName(), 50));
        sb.append(Fields.padRight(t.getMerchantCity(), 50));
        sb.append(Fields.padRight(t.getMerchantZip(), 10));
        sb.append(Fields.padRight(t.getCardNumber(), 16));
        sb.append(Fields.padRight(t.getOriginationTimestamp(), 26));
        sb.append(Fields.padRight(t.getProcessingTimestamp(), 26));
        sb.append(Fields.padRight("", 20));
        return sb.toString();
    }
}
