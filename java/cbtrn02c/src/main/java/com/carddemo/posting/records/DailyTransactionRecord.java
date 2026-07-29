package com.carddemo.posting.records;

import com.carddemo.interest.cobol.Zoned;
import com.carddemo.interest.records.TransactionRecord;

import java.math.BigDecimal;

/**
 * Copybook CVTRA06Y - one claimed transaction from the overnight feed, 350 bytes.
 *
 * <p>Field for field identical to CVTRA05Y (the transaction master layout), which is why
 * {@link #toPostedTransaction(String)} is a straight copy with one field replaced.
 *
 * <p>{@code raw} is the record exactly as it was read. CBTRN02C copies it verbatim into the
 * first 350 bytes of a reject record ({@code 2500-WRITE-REJECT-REC}), so it must survive
 * parsing untouched.
 */
public record DailyTransactionRecord(String transactionId, String typeCode, String categoryCode, String source,
                                     String description, BigDecimal amount, long merchantId, String merchantName,
                                     String merchantCity, String merchantZip, String cardNumber,
                                     String originTimestamp, String processTimestamp, String raw) {

    public static final int LENGTH = 350;

    public static DailyTransactionRecord parse(String line) {
        String record = pad(line);
        return new DailyTransactionRecord(
                record.substring(0, 16),
                record.substring(16, 18),
                record.substring(18, 22),
                record.substring(22, 32),
                record.substring(32, 132),
                Zoned.parseSigned(record.substring(132, 143), 2),
                Zoned.parseUnsigned(record.substring(143, 152)),
                record.substring(152, 202),
                record.substring(202, 252),
                record.substring(252, 262),
                record.substring(262, 278),
                record.substring(278, 304),
                record.substring(304, 330),
                record);
    }

    /** ACCT-EXPIRAION-DATE is compared against DALYTRAN-ORIG-TS (1:10) - see rule R9. */
    public String originDate() {
        return originTimestamp.substring(0, 10);
    }

    /**
     * 2000-POST-TRANSACTION: every business field is moved across unchanged and only
     * TRAN-PROC-TS is replaced with the run's own DB2 format timestamp.
     */
    public TransactionRecord toPostedTransaction(String processedTimestamp) {
        return new TransactionRecord(transactionId, typeCode, categoryCode, source, description, amount,
                merchantId, merchantName, merchantCity, merchantZip, cardNumber, originTimestamp,
                processedTimestamp);
    }

    private static String pad(String line) {
        String text = line == null ? "" : line;
        return text.length() >= LENGTH ? text.substring(0, LENGTH) : text + " ".repeat(LENGTH - text.length());
    }
}
