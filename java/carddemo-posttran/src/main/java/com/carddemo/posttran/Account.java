package com.carddemo.posttran;

import java.math.BigDecimal;

/** ACCOUNT-RECORD (app/cpy/CVACT01Y.cpy). */
public class Account {

    private long acctId;
    private String activeStatus;
    private BigDecimal currBal = BigDecimal.ZERO.setScale(2);
    private BigDecimal creditLimit = BigDecimal.ZERO.setScale(2);
    private BigDecimal cashCreditLimit = BigDecimal.ZERO.setScale(2);
    private String openDate;
    private String expirationDate;
    private String reissueDate;
    private BigDecimal currCycCredit = BigDecimal.ZERO.setScale(2);
    private BigDecimal currCycDebit = BigDecimal.ZERO.setScale(2);
    private String addrZip;
    private String groupId;

    public long getAcctId() {
        return acctId;
    }

    public void setAcctId(long acctId) {
        this.acctId = acctId;
    }

    public String getActiveStatus() {
        return activeStatus;
    }

    public void setActiveStatus(String activeStatus) {
        this.activeStatus = activeStatus;
    }

    public BigDecimal getCurrBal() {
        return currBal;
    }

    public void setCurrBal(BigDecimal currBal) {
        this.currBal = Pic.amount(currBal);
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public void setCreditLimit(BigDecimal creditLimit) {
        this.creditLimit = Pic.amount(creditLimit);
    }

    public BigDecimal getCashCreditLimit() {
        return cashCreditLimit;
    }

    public void setCashCreditLimit(BigDecimal cashCreditLimit) {
        this.cashCreditLimit = Pic.amount(cashCreditLimit);
    }

    public String getOpenDate() {
        return openDate;
    }

    public void setOpenDate(String openDate) {
        this.openDate = openDate;
    }

    public String getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(String expirationDate) {
        this.expirationDate = expirationDate;
    }

    public String getReissueDate() {
        return reissueDate;
    }

    public void setReissueDate(String reissueDate) {
        this.reissueDate = reissueDate;
    }

    public BigDecimal getCurrCycCredit() {
        return currCycCredit;
    }

    public void setCurrCycCredit(BigDecimal currCycCredit) {
        this.currCycCredit = Pic.amount(currCycCredit);
    }

    public BigDecimal getCurrCycDebit() {
        return currCycDebit;
    }

    public void setCurrCycDebit(BigDecimal currCycDebit) {
        this.currCycDebit = Pic.amount(currCycDebit);
    }

    public String getAddrZip() {
        return addrZip;
    }

    public void setAddrZip(String addrZip) {
        this.addrZip = addrZip;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }
}
