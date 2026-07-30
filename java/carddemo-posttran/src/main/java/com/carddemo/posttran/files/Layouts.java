package com.carddemo.posttran.files;

import com.carddemo.posttran.Account;
import com.carddemo.posttran.CardXref;
import com.carddemo.posttran.DailyTransaction;
import com.carddemo.posttran.RejectRecord;
import com.carddemo.posttran.TranCatBalance;
import com.carddemo.posttran.Transaction;

/**
 * Record layouts of the CardDemo copybooks used by CBTRN02C: CVTRA06Y (DALYTRAN, 350),
 * CVTRA05Y (TRAN, 350), CVACT03Y (CARD-XREF, 50), CVACT01Y (ACCOUNT, 300) and
 * CVTRA01Y (TRAN-CAT-BAL, 50), plus the 430 byte DALYREJS record of CBTRN02C itself.
 */
public final class Layouts {

    public static final int DALYTRAN_LENGTH = 350;
    public static final int TRAN_LENGTH = 350;
    public static final int XREF_LENGTH = 50;
    public static final int ACCOUNT_LENGTH = 300;
    public static final int TRAN_CAT_BAL_LENGTH = 50;
    public static final int REJECT_LENGTH = 430;

    private Layouts() {
    }

    public static DailyTransaction dailyTransaction(String record) {
        String r = pad(record, DALYTRAN_LENGTH);
        DailyTransaction t = new DailyTransaction();
        t.setId(Cobol.text(r, 0, 16));
        t.setTypeCd(Cobol.text(r, 16, 2));
        t.setCatCd(Cobol.digits(r, 18, 4));
        t.setSource(Cobol.text(r, 22, 10));
        t.setDesc(Cobol.text(r, 32, 100));
        t.setAmt(Cobol.decimal(r, 132, 11, 2));
        t.setMerchantId(Cobol.digits(r, 143, 9));
        t.setMerchantName(Cobol.text(r, 152, 50));
        t.setMerchantCity(Cobol.text(r, 202, 50));
        t.setMerchantZip(Cobol.text(r, 252, 10));
        t.setCardNum(Cobol.text(r, 262, 16));
        t.setOrigTs(Cobol.text(r, 278, 26));
        t.setProcTs(Cobol.text(r, 304, 26));
        return t;
    }

    public static String dailyTransaction(DailyTransaction t) {
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

    public static CardXref xref(String record) {
        String r = pad(record, XREF_LENGTH);
        return new CardXref(Cobol.text(r, 0, 16), Cobol.digits(r, 16, 9), Long.parseLong(Cobol.digits(r, 25, 11)));
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
                + " ".repeat(178);
    }

    public static TranCatBalance tranCatBalance(String record) {
        String r = pad(record, TRAN_CAT_BAL_LENGTH);
        return new TranCatBalance(Long.parseLong(Cobol.digits(r, 0, 11)),
                Cobol.text(r, 11, 2),
                Cobol.digits(r, 13, 4),
                Cobol.decimal(r, 17, 11, 2));
    }

    public static String tranCatBalance(TranCatBalance b) {
        return tranCatBalance(b, null);
    }

    /** @param filler FILLER of the record this one replaces, {@code null} for a new record. */
    public static String tranCatBalance(TranCatBalance b, String filler) {
        return Cobol.putDigits(b.getAcctId(), 11)
                + Cobol.putText(b.getTypeCd(), 2)
                + Cobol.putDigits(b.getCatCd(), 4)
                + Cobol.putDecimal(b.getBalance(), 11, 2)
                + Cobol.putText(filler, 22);
    }

    /** The 22 byte FILLER of CVTRA01Y, which carries no business data. */
    public static String tranCatBalanceFiller(String record) {
        return Cobol.text(pad(record, TRAN_CAT_BAL_LENGTH), 28, 22);
    }

    /** REJECT-RECORD: the daily transaction record followed by WS-VALIDATION-TRAILER. */
    public static String reject(RejectRecord reject) {
        return dailyTransaction(reject.transaction())
                + Cobol.putDigits(reject.failReason(), 4)
                + Cobol.putText(reject.failReasonDesc(), 76);
    }

    private static String pad(String record, int length) {
        if (record.length() >= length) {
            return record;
        }
        return record + " ".repeat(length - record.length());
    }
}
