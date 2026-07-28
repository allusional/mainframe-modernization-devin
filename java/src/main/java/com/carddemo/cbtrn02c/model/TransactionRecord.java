package com.carddemo.cbtrn02c.model;

import com.carddemo.cbtrn02c.io.FixedWidth;
import com.carddemo.cbtrn02c.io.ZonedDecimal;

import java.math.BigDecimal;

/**
 * Posted transaction output record. COBOL copybook: CVTRA05Y (TRAN-RECORD, 350 bytes).
 */
public final class TransactionRecord {

    public static final int RECORD_LENGTH = 350;

    private String id;
    private String typeCode;
    private String categoryCode;
    private String source;
    private String description;
    private BigDecimal amount;
    private long merchantId;
    private String merchantName;
    private String merchantCity;
    private String merchantZip;
    private String cardNumber;
    private String originalTimestamp;
    private String processingTimestamp;

    /**
     * Builds the transaction record from a validated daily transaction, mirroring the
     * MOVE statements in paragraph 2000-POST-TRANSACTION.
     */
    public static TransactionRecord fromDailyTransaction(DailyTransactionRecord daly, String processingTimestamp) {
        TransactionRecord t = new TransactionRecord();
        t.id = daly.getId();
        t.typeCode = daly.getTypeCode();
        t.categoryCode = daly.getCategoryCode();
        t.source = daly.getSource();
        t.description = daly.getDescription();
        t.amount = daly.getAmount();
        t.merchantId = daly.getMerchantId();
        t.merchantName = daly.getMerchantName();
        t.merchantCity = daly.getMerchantCity();
        t.merchantZip = daly.getMerchantZip();
        t.cardNumber = daly.getCardNumber();
        t.originalTimestamp = daly.getOriginalTimestamp();
        t.processingTimestamp = processingTimestamp;
        return t;
    }

    /** Serialises to the 350-byte fixed-width layout (used when writing TRANFILE). */
    public String toRecord() {
        StringBuilder sb = new StringBuilder(RECORD_LENGTH);
        sb.append(FixedWidth.alpha(id, 16));
        sb.append(FixedWidth.alpha(typeCode, 2));
        sb.append(FixedWidth.alpha(categoryCode, 4));
        sb.append(FixedWidth.alpha(source, 10));
        sb.append(FixedWidth.alpha(description, 100));
        sb.append(ZonedDecimal.encodeSigned(amount, 11, 2));
        sb.append(FixedWidth.numeric(merchantId, 9));
        sb.append(FixedWidth.alpha(merchantName, 50));
        sb.append(FixedWidth.alpha(merchantCity, 50));
        sb.append(FixedWidth.alpha(merchantZip, 10));
        sb.append(FixedWidth.alpha(cardNumber, 16));
        sb.append(FixedWidth.alpha(originalTimestamp, 26));
        sb.append(FixedWidth.alpha(processingTimestamp, 26));
        sb.append(" ".repeat(20));
        return sb.toString();
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

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public String getProcessingTimestamp() {
        return processingTimestamp;
    }
}
