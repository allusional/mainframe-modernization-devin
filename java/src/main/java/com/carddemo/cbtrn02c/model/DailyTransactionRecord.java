package com.carddemo.cbtrn02c.model;

import com.carddemo.cbtrn02c.io.FixedWidth;
import com.carddemo.cbtrn02c.io.ZonedDecimal;

import java.math.BigDecimal;

/**
 * Daily transaction input record. COBOL copybook: CVTRA06Y (DALYTRAN-RECORD, 350 bytes).
 */
public final class DailyTransactionRecord {

    public static final int RECORD_LENGTH = 350;

    private final String id;
    private final String typeCode;
    private final String categoryCode;
    private final String source;
    private final String description;
    private final BigDecimal amount;
    private final long merchantId;
    private final String merchantName;
    private final String merchantCity;
    private final String merchantZip;
    private final String cardNumber;
    private final String originalTimestamp;
    private final String processingTimestamp;

    private final String rawRecord;

    public DailyTransactionRecord(String id, String typeCode, String categoryCode, String source,
                                  String description, BigDecimal amount, long merchantId,
                                  String merchantName, String merchantCity, String merchantZip,
                                  String cardNumber, String originalTimestamp, String processingTimestamp,
                                  String rawRecord) {
        this.id = id;
        this.typeCode = typeCode;
        this.categoryCode = categoryCode;
        this.source = source;
        this.description = description;
        this.amount = amount;
        this.merchantId = merchantId;
        this.merchantName = merchantName;
        this.merchantCity = merchantCity;
        this.merchantZip = merchantZip;
        this.cardNumber = cardNumber;
        this.originalTimestamp = originalTimestamp;
        this.processingTimestamp = processingTimestamp;
        this.rawRecord = rawRecord;
    }

    public static DailyTransactionRecord parse(String line) {
        String rec = FixedWidth.slice(line, 0, RECORD_LENGTH);
        return new DailyTransactionRecord(
                FixedWidth.slice(rec, 0, 16),
                FixedWidth.slice(rec, 16, 2),
                FixedWidth.slice(rec, 18, 4),
                FixedWidth.slice(rec, 22, 10),
                FixedWidth.slice(rec, 32, 100),
                ZonedDecimal.decodeSigned(FixedWidth.slice(rec, 132, 11), 2),
                Long.parseLong(FixedWidth.slice(rec, 143, 9).trim()),
                FixedWidth.slice(rec, 152, 50),
                FixedWidth.slice(rec, 202, 50),
                FixedWidth.slice(rec, 252, 10),
                FixedWidth.slice(rec, 262, 16),
                FixedWidth.slice(rec, 278, 26),
                FixedWidth.slice(rec, 304, 26),
                rec);
    }

    public String getId() {
        return id;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public String getSource() {
        return source;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public long getMerchantId() {
        return merchantId;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public String getMerchantCity() {
        return merchantCity;
    }

    public String getMerchantZip() {
        return merchantZip;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public String getOriginalTimestamp() {
        return originalTimestamp;
    }

    public String getProcessingTimestamp() {
        return processingTimestamp;
    }

    /** The original fixed-width record, used verbatim as reject output (REJECT-TRAN-DATA). */
    public String getRawRecord() {
        return rawRecord;
    }
}
