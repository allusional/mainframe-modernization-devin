package com.carddemo.intcalc.files;

import com.carddemo.intcalc.Account;
import com.carddemo.intcalc.CardXref;
import com.carddemo.intcalc.Cobol;
import com.carddemo.intcalc.DiscGroup;
import com.carddemo.intcalc.DiscGroupKey;
import com.carddemo.intcalc.TranCatBalance;
import com.carddemo.intcalc.Transaction;

/**
 * Record layouts of the CardDemo copybooks used by CBACT04C: CVTRA01Y (TRAN-CAT-BAL, 50),
 * CVACT03Y (CARD-XREF, 50), CVTRA02Y (DIS-GROUP, 50), CVACT01Y (ACCOUNT, 300) and
 * CVTRA05Y (TRAN, 350).
 */
public final class Layouts {

    public static final int TRAN_CAT_BAL_LENGTH = 50;
    public static final int XREF_LENGTH = 50;
    public static final int DISC_GROUP_LENGTH = 50;
    public static final int ACCOUNT_LENGTH = 300;
    public static final int TRAN_LENGTH = 350;

    private Layouts() {
    }

    public static TranCatBalance tranCatBalance(String record) {
        String r = pad(record, TRAN_CAT_BAL_LENGTH);
        return new TranCatBalance(Long.parseLong(Cobol.digits(r, 0, 11)),
                Cobol.text(r, 11, 2),
                Cobol.digits(r, 13, 4),
                Cobol.decimal(r, 17, 11, 2),
                Cobol.text(r, 28, 22));
    }

    public static String tranCatBalance(TranCatBalance balance) {
        return balance.image();
    }

    public static CardXref xref(String record) {
        String r = pad(record, XREF_LENGTH);
        return new CardXref(Cobol.text(r, 0, 16), Cobol.digits(r, 16, 9), Long.parseLong(Cobol.digits(r, 25, 11)));
    }

    public static String xref(CardXref xref) {
        return Cobol.putText(xref.cardNum(), 16)
                + Cobol.putDigits(xref.custId(), 9)
                + Cobol.putDigits(xref.acctId(), 11)
                + " ".repeat(14);
    }

    public static DiscGroup discGroup(String record) {
        String r = pad(record, DISC_GROUP_LENGTH);
        return new DiscGroup(new DiscGroupKey(Cobol.text(r, 0, 10), Cobol.text(r, 10, 2), Cobol.digits(r, 12, 4)),
                Cobol.decimal(r, 16, 6, 2));
    }

    public static String discGroup(DiscGroup group) {
        return discGroup(group, "");
    }

    /** @param filler the 28 byte FILLER of CVTRA02Y, which holds zeroes in the sample dataset. */
    public static String discGroup(DiscGroup group, String filler) {
        return group.key().image() + Cobol.putDecimal(group.intRate(), 6, 2) + Cobol.putText(filler, 28);
    }

    /** The 28 byte FILLER of CVTRA02Y, which carries no business data. */
    public static String discGroupFiller(String record) {
        return Cobol.text(pad(record, DISC_GROUP_LENGTH), 22, 28);
    }

    public static Account account(String record) {
        String r = pad(record, ACCOUNT_LENGTH);
        Account a = new Account();
        a.setAcctId(Long.parseLong(Cobol.digits(r, 0, 11)));
        a.setActiveStatus(Cobol.text(r, 11, 1));
        a.setCurrBal(Cobol.decimal(r, 12, 12, 2));
        a.setCreditLimit(Cobol.decimal(r, 24, 12, 2));
        a.setCashCreditLimit(Cobol.decimal(r, 36, 12, 2));
        a.setOpenDate(Cobol.text(r, 48, 10));
        a.setExpirationDate(Cobol.text(r, 58, 10));
        a.setReissueDate(Cobol.text(r, 68, 10));
        a.setCurrCycCredit(Cobol.decimal(r, 78, 12, 2));
        a.setCurrCycDebit(Cobol.decimal(r, 90, 12, 2));
        a.setAddrZip(Cobol.text(r, 102, 10));
        a.setGroupId(Cobol.text(r, 112, 10));
        return a;
    }

    public static String account(Account a) {
        return account(a, "");
    }

    /** @param filler the 178 byte FILLER of the record read from the file, which a REWRITE keeps. */
    public static String account(Account a, String filler) {
        return Cobol.putDigits(a.getAcctId(), 11)
                + Cobol.putText(a.getActiveStatus(), 1)
                + Cobol.putDecimal(a.getCurrBal(), 12, 2)
                + Cobol.putDecimal(a.getCreditLimit(), 12, 2)
                + Cobol.putDecimal(a.getCashCreditLimit(), 12, 2)
                + Cobol.putText(a.getOpenDate(), 10)
                + Cobol.putText(a.getExpirationDate(), 10)
                + Cobol.putText(a.getReissueDate(), 10)
                + Cobol.putDecimal(a.getCurrCycCredit(), 12, 2)
                + Cobol.putDecimal(a.getCurrCycDebit(), 12, 2)
                + Cobol.putText(a.getAddrZip(), 10)
                + Cobol.putText(a.getGroupId(), 10)
                + Cobol.putText(filler, 178);
    }

    /** The 178 byte FILLER of CVACT01Y, which carries no business data. */
    public static String accountFiller(String record) {
        return Cobol.text(pad(record, ACCOUNT_LENGTH), 122, 178);
    }

    public static String transaction(Transaction t) {
        return Cobol.putText(t.getId(), 16)
                + Cobol.putText(t.getTypeCd(), 2)
                + Cobol.putDigits(t.getCatCd(), 4)
                + Cobol.putText(t.getSource(), 10)
                + Cobol.putText(t.getDesc(), 100)
                + Cobol.putDecimal(t.getAmt(), 11, 2)
                + Cobol.putDigits(t.getMerchantId(), 9)
                + Cobol.putText(t.getMerchantName(), 50)
                + Cobol.putText(t.getMerchantCity(), 50)
                + Cobol.putText(t.getMerchantZip(), 10)
                + Cobol.putText(t.getCardNum(), 16)
                + Cobol.putText(t.getOrigTs(), 26)
                + Cobol.putText(t.getProcTs(), 26)
                + " ".repeat(20);
    }

    private static String pad(String record, int length) {
        if (record.length() >= length) {
            return record;
        }
        return record + " ".repeat(length - record.length());
    }
}
