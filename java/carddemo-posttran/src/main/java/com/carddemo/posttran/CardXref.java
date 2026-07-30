package com.carddemo.posttran;

/** CARD-XREF-RECORD (app/cpy/CVACT03Y.cpy). */
public class CardXref {

    private String cardNum;
    private String custId;
    private long acctId;

    public CardXref() {
    }

    public CardXref(String cardNum, String custId, long acctId) {
        this.cardNum = cardNum;
        this.custId = custId;
        this.acctId = acctId;
    }

    public String getCardNum() {
        return cardNum;
    }

    public void setCardNum(String cardNum) {
        this.cardNum = cardNum;
    }

    public String getCustId() {
        return custId;
    }

    public void setCustId(String custId) {
        this.custId = custId;
    }

    public long getAcctId() {
        return acctId;
    }

    public void setAcctId(long acctId) {
        this.acctId = acctId;
    }
}
