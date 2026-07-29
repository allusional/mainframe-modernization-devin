package com.carddemo.posting;

import com.carddemo.interest.cobol.Zoned;
import com.carddemo.interest.records.AccountRecord;
import com.carddemo.interest.records.CardXrefRecord;
import com.carddemo.interest.records.TranCatBalRecord;
import com.carddemo.posting.records.DailyTransactionRecord;

import java.math.BigDecimal;

/**
 * Builders for the fixed-width records the tests need, laid out from the copybooks so that a
 * test reads as "an account with a 1000.00 limit" rather than as a 300 character string.
 */
public final class Fixtures {

    public static final String CARD = "4111111111111111";
    public static final long ACCOUNT = 11111111111L;

    private Fixtures() {
    }

    /** CVACT01Y, 300 bytes. */
    public static AccountRecord account(long accountId, String creditLimit, String currentBalance,
                                 String cycleCredit, String cycleDebit, String expirationDate) {
        String record = Zoned.formatUnsigned(accountId, 11)
                + "Y"
                + Zoned.formatSigned(new BigDecimal(currentBalance), 12, 2)
                + Zoned.formatSigned(new BigDecimal(creditLimit), 12, 2)
                + Zoned.formatSigned(new BigDecimal("500.00"), 12, 2)
                + "2020-01-01"
                + Zoned.alphanumeric(expirationDate, 10)
                + "2020-01-01"
                + Zoned.formatSigned(new BigDecimal(cycleCredit), 12, 2)
                + Zoned.formatSigned(new BigDecimal(cycleDebit), 12, 2)
                + "12345     "
                + "DEFAULT   "
                + " ".repeat(AccountRecord.LENGTH - 122);
        return AccountRecord.parse(record);
    }

    /** An account with no cycle activity, a 1000.00 limit and a distant expiry date. */
    public static AccountRecord account() {
        return account(ACCOUNT, "1000.00", "0.00", "0.00", "0.00", "2099-12-31");
    }

    /** CVACT03Y, 50 bytes. */
    public static CardXrefRecord xref(String cardNumber, long accountId) {
        return CardXrefRecord.parse(Zoned.alphanumeric(cardNumber, 16)
                + Zoned.formatUnsigned(999999999L, 9)
                + Zoned.formatUnsigned(accountId, 11)
                + " ".repeat(14));
    }

    public static CardXrefRecord xref() {
        return xref(CARD, ACCOUNT);
    }

    /** CVTRA01Y, 50 bytes. */
    public static TranCatBalRecord categoryBalance(long accountId, String typeCode, String categoryCode, String balance) {
        return TranCatBalRecord.parse(Zoned.formatUnsigned(accountId, 11)
                + Zoned.alphanumeric(typeCode, 2)
                + Zoned.alphanumeric(categoryCode, 4)
                + Zoned.formatSigned(new BigDecimal(balance), 11, 2)
                + " ".repeat(22));
    }

    /** CVTRA06Y, 350 bytes. */
    public static DailyTransactionRecord dailyTransaction(String transactionId, String cardNumber, String amount,
                                                   String originTimestamp) {
        return dailyTransaction(transactionId, cardNumber, amount, originTimestamp, "01", "0001");
    }

    public static DailyTransactionRecord dailyTransaction(String transactionId, String cardNumber, String amount,
                                                   String originTimestamp, String typeCode, String categoryCode) {
        String record = Zoned.alphanumeric(transactionId, 16)
                + Zoned.alphanumeric(typeCode, 2)
                + Zoned.alphanumeric(categoryCode, 4)
                + Zoned.alphanumeric("POS TERM", 10)
                + Zoned.alphanumeric("A TEST TRANSACTION", 100)
                + Zoned.formatSigned(new BigDecimal(amount), 11, 2)
                + Zoned.formatUnsigned(123456789L, 9)
                + Zoned.alphanumeric("A MERCHANT", 50)
                + Zoned.alphanumeric("A CITY", 50)
                + Zoned.alphanumeric("12345", 10)
                + Zoned.alphanumeric(cardNumber, 16)
                + Zoned.alphanumeric(originTimestamp, 26)
                + Zoned.alphanumeric("", 26)
                + " ".repeat(20);
        return DailyTransactionRecord.parse(record);
    }

    /** A transaction that passes every rule against {@link #account()}. */
    public static DailyTransactionRecord dailyTransaction(String amount) {
        return dailyTransaction("0000000000000001", CARD, amount, "2024-06-01-12.00.00.000000");
    }
}
