package com.carddemo.cbtrn02c.testsupport;

import com.carddemo.cbtrn02c.copybook.CobolField;

import java.math.BigDecimal;

/** Builders for synthetic fixed width record images, used by the unit tests. */
public final class RecordImages {

    private RecordImages() {
    }

    /** A DALYTRAN record image (CVTRA06Y, 350 bytes). */
    public static String dalyTran(String tranId, String typeCd, String catCd, BigDecimal amount,
                                 String cardNumber, String origTs) {
        return CobolField.moveAlpha(tranId, 16)
                + CobolField.moveAlpha(typeCd, 2)
                + catCd
                + CobolField.moveAlpha("POS TERM", 10)
                + CobolField.moveAlpha("Purchase", 100)
                + CobolField.formatSigned(amount, 11, 2)
                + "800000000"
                + CobolField.moveAlpha("Merchant", 50)
                + CobolField.moveAlpha("City", 50)
                + CobolField.moveAlpha("72112", 10)
                + CobolField.moveAlpha(cardNumber, 16)
                + CobolField.moveAlpha(origTs, 26)
                + " ".repeat(26)
                + " ".repeat(20);
    }

    /** A CARD-XREF record image (CVACT03Y, 50 bytes). */
    public static String cardXref(String cardNumber, String customerId, String accountId) {
        return CobolField.moveAlpha(cardNumber, 16) + customerId + accountId + " ".repeat(14);
    }

    /** An ACCOUNT record image (CVACT01Y, 300 bytes). */
    public static String account(String accountId, BigDecimal currentBalance, BigDecimal creditLimit,
                                 BigDecimal cycleCredit, BigDecimal cycleDebit, String expirationDate) {
        return accountId
                + "Y"
                + CobolField.formatSigned(currentBalance, 12, 2)
                + CobolField.formatSigned(creditLimit, 12, 2)
                + CobolField.formatSigned(creditLimit, 12, 2)
                + "2014-11-20"
                + CobolField.moveAlpha(expirationDate, 10)
                + "2025-05-20"
                + CobolField.formatSigned(cycleCredit, 12, 2)
                + CobolField.formatSigned(cycleDebit, 12, 2)
                + CobolField.moveAlpha("A00000000", 10)
                + " ".repeat(10)
                + " ".repeat(178);
    }

    /** A TRAN-CAT-BAL record image (CVTRA01Y, 50 bytes). */
    public static String tranCatBal(String accountId, String typeCd, String catCd, BigDecimal balance) {
        return accountId + CobolField.moveAlpha(typeCd, 2) + catCd
                + CobolField.formatSigned(balance, 11, 2) + " ".repeat(22);
    }
}
