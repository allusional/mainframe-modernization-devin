package com.carddemo.posttran;

import java.math.BigDecimal;

/** TRAN-CAT-BAL-RECORD (app/cpy/CVTRA01Y.cpy). */
public class TranCatBalance {

    private long acctId;
    private String typeCd;
    private String catCd;
    private BigDecimal balance = BigDecimal.ZERO.setScale(2);

    public TranCatBalance() {
    }

    public TranCatBalance(long acctId, String typeCd, String catCd, BigDecimal balance) {
        this.acctId = acctId;
        this.typeCd = typeCd;
        this.catCd = catCd;
        setBalance(balance);
    }

    public long getAcctId() {
        return acctId;
    }

    public void setAcctId(long acctId) {
        this.acctId = acctId;
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
        this.balance = Pic.amount(balance);
    }

    public TranCatBalanceKey key() {
        return new TranCatBalanceKey(acctId, typeCd, catCd);
    }
}
