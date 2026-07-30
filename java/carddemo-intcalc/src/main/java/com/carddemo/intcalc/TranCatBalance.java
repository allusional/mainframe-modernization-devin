package com.carddemo.intcalc;

import java.math.BigDecimal;

/**
 * TRAN-CAT-BAL-RECORD (app/cpy/CVTRA01Y.cpy), the TCATBALF record CBACT04C reads sequentially.
 *
 * <p>The 22 byte FILLER is carried along because the program does
 * {@code DISPLAY TRAN-CAT-BAL-RECORD}, which prints the whole 50 byte record image.
 */
public class TranCatBalance {

    private long acctId;
    private String typeCd = "";
    private String catCd = "0000";
    private BigDecimal balance = BigDecimal.ZERO.setScale(2);
    private String filler = "";

    public TranCatBalance() {
    }

    public TranCatBalance(long acctId, String typeCd, String catCd, BigDecimal balance) {
        this(acctId, typeCd, catCd, balance, "");
    }

    public TranCatBalance(long acctId, String typeCd, String catCd, BigDecimal balance, String filler) {
        this.acctId = acctId;
        this.typeCd = typeCd;
        this.catCd = catCd;
        setBalance(balance);
        this.filler = filler;
    }

    public long getAcctId() {
        return acctId;
    }

    public void setAcctId(long acctId) {
        this.acctId = acctId;
    }

    /** TRANCAT-ACCT-ID as the 11 digits the record holds. */
    public String getAcctIdText() {
        return Cobol.putDigits(acctId, 11);
    }

    public String getTypeCd() {
        return typeCd;
    }

    public void setTypeCd(String typeCd) {
        this.typeCd = typeCd;
    }

    public String getCatCd() {
        return catCd;
    }

    public void setCatCd(String catCd) {
        this.catCd = catCd;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = Cobol.amount(balance, 9, 2);
    }

    public String getFiller() {
        return filler;
    }

    public void setFiller(String filler) {
        this.filler = filler;
    }

    /** The 50 byte record image, what {@code DISPLAY TRAN-CAT-BAL-RECORD} writes to SYSOUT. */
    public String image() {
        return Cobol.putDigits(acctId, 11)
                + Cobol.putText(typeCd, 2)
                + Cobol.putDigits(catCd, 4)
                + Cobol.putDecimal(balance, 11, 2)
                + Cobol.putText(filler, 22);
    }
}
