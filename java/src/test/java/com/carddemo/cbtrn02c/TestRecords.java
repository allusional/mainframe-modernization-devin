package com.carddemo.cbtrn02c;

import com.carddemo.cbtrn02c.io.FixedWidth;
import com.carddemo.cbtrn02c.io.ZonedDecimal;

import java.math.BigDecimal;

/**
 * Builds synthetic fixed-width record lines for deterministic unit tests, using the
 * same field layouts as the CardDemo copybooks.
 */
public final class TestRecords {

    private TestRecords() {
    }

    public static String dailyTran(String id, String type, String category, BigDecimal amount,
                            String cardNumber, String originalTimestamp) {
        StringBuilder sb = new StringBuilder();
        sb.append(FixedWidth.alpha(id, 16));
        sb.append(FixedWidth.alpha(type, 2));
        sb.append(FixedWidth.numeric(Long.parseLong(category), 4));
        sb.append(FixedWidth.alpha("TESTSRC", 10));
        sb.append(FixedWidth.alpha("Test transaction", 100));
        sb.append(ZonedDecimal.encodeSigned(amount, 11, 2));
        sb.append(FixedWidth.numeric(123456789L, 9));
        sb.append(FixedWidth.alpha("Test Merchant", 50));
        sb.append(FixedWidth.alpha("Test City", 50));
        sb.append(FixedWidth.alpha("12345", 10));
        sb.append(FixedWidth.alpha(cardNumber, 16));
        sb.append(FixedWidth.alpha(originalTimestamp, 26));
        sb.append(FixedWidth.alpha("", 26));
        sb.append(FixedWidth.alpha("", 20));
        return sb.toString();
    }

    public static String account(long id, BigDecimal currentBalance, BigDecimal creditLimit,
                          String expirationDate, BigDecimal cycleCredit, BigDecimal cycleDebit) {
        StringBuilder sb = new StringBuilder();
        sb.append(FixedWidth.numeric(id, 11));
        sb.append("Y");
        sb.append(ZonedDecimal.encodeSigned(currentBalance, 12, 2));
        sb.append(ZonedDecimal.encodeSigned(creditLimit, 12, 2));
        sb.append(ZonedDecimal.encodeSigned(new BigDecimal("5000.00"), 12, 2));
        sb.append(FixedWidth.alpha("2010-01-01", 10));
        sb.append(FixedWidth.alpha(expirationDate, 10));
        sb.append(FixedWidth.alpha("2020-01-01", 10));
        sb.append(ZonedDecimal.encodeSigned(cycleCredit, 12, 2));
        sb.append(ZonedDecimal.encodeSigned(cycleDebit, 12, 2));
        sb.append(FixedWidth.alpha("99999", 10));
        sb.append(FixedWidth.alpha("GRP1", 10));
        sb.append(FixedWidth.alpha("", 178));
        return sb.toString();
    }

    public static String xref(String cardNumber, long customerId, long accountId) {
        return FixedWidth.alpha(cardNumber, 16)
                + FixedWidth.numeric(customerId, 9)
                + FixedWidth.numeric(accountId, 11);
    }

    public static String tranCatBal(long accountId, String type, String category, BigDecimal balance) {
        return FixedWidth.numeric(accountId, 11)
                + FixedWidth.alpha(type, 2)
                + FixedWidth.numeric(Long.parseLong(category), 4)
                + ZonedDecimal.encodeSigned(balance, 11, 2)
                + FixedWidth.alpha("", 22);
    }
}
