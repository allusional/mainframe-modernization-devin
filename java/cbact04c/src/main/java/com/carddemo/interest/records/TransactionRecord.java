package com.carddemo.interest.records;

import com.carddemo.interest.cobol.Zoned;

import java.math.BigDecimal;

/** Copybook CVTRA05Y - transaction record, 350 bytes. */
public record TransactionRecord(String transactionId, String typeCode, String categoryCode, String source,
                                String description, BigDecimal amount, long merchantId, String merchantName,
                                String merchantCity, String merchantZip, String cardNumber, String originTimestamp,
                                String processTimestamp) {

    public static final int LENGTH = 350;

    public String toRecord() {
        String record = Zoned.alphanumeric(transactionId, 16)
                + Zoned.alphanumeric(typeCode, 2)
                + Zoned.alphanumeric(categoryCode, 4)
                + Zoned.alphanumeric(source, 10)
                + Zoned.alphanumeric(description, 100)
                + Zoned.formatSigned(amount, 11, 2)
                + Zoned.formatUnsigned(merchantId, 9)
                + Zoned.alphanumeric(merchantName, 50)
                + Zoned.alphanumeric(merchantCity, 50)
                + Zoned.alphanumeric(merchantZip, 10)
                + Zoned.alphanumeric(cardNumber, 16)
                + Zoned.alphanumeric(originTimestamp, 26)
                + Zoned.alphanumeric(processTimestamp, 26)
                + " ".repeat(20);
        if (record.length() != LENGTH) {
            throw new IllegalStateException("Transaction record is " + record.length() + " bytes, expected " + LENGTH);
        }
        return record;
    }

    public static TransactionRecord parse(String line) {
        String record = Records.pad(line, LENGTH);
        return new TransactionRecord(
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
                record.substring(304, 330));
    }
}
