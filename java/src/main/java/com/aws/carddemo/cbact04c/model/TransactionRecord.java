package com.aws.carddemo.cbact04c.model;

import java.math.BigDecimal;

/**
 * Transaction record (copybook CVTRA05Y, RECLN = 350). Produced by the interest
 * calculator for every non-zero monthly interest amount.
 * <pre>
 *   TRAN-ID            PIC X(16)
 *   TRAN-TYPE-CD       PIC X(02)
 *   TRAN-CAT-CD        PIC 9(04)
 *   TRAN-SOURCE        PIC X(10)
 *   TRAN-DESC          PIC X(100)
 *   TRAN-AMT           PIC S9(09)V99
 *   TRAN-MERCHANT-ID   PIC 9(09)
 *   TRAN-MERCHANT-NAME PIC X(50)
 *   TRAN-MERCHANT-CITY PIC X(50)
 *   TRAN-MERCHANT-ZIP  PIC X(10)
 *   TRAN-CARD-NUM      PIC X(16)
 *   TRAN-ORIG-TS       PIC X(26)
 *   TRAN-PROC-TS       PIC X(26)
 *   FILLER             PIC X(20)
 * </pre>
 */
public class TransactionRecord {

    private final String id;
    private final String typeCode;
    private final int categoryCode;
    private final String source;
    private final String description;
    private final BigDecimal amount;
    private final long merchantId;
    private final String merchantName;
    private final String merchantCity;
    private final String merchantZip;
    private final String cardNumber;
    private final String originationTimestamp;
    private final String processingTimestamp;

    public TransactionRecord(String id, String typeCode, int categoryCode, String source,
                             String description, BigDecimal amount, long merchantId,
                             String merchantName, String merchantCity, String merchantZip,
                             String cardNumber, String originationTimestamp,
                             String processingTimestamp) {
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
        this.originationTimestamp = originationTimestamp;
        this.processingTimestamp = processingTimestamp;
    }

    public String getId() {
        return id;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public int getCategoryCode() {
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

    public String getOriginationTimestamp() {
        return originationTimestamp;
    }

    public String getProcessingTimestamp() {
        return processingTimestamp;
    }
}
