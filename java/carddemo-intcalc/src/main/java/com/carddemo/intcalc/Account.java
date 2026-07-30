package com.carddemo.intcalc;

import java.math.BigDecimal;

/** ACCOUNT-RECORD (app/cpy/CVACT01Y.cpy), the ACCTFILE record CBACT04C reads and rewrites. */
public class Account {

    private long acctId;
    private String activeStatus = "";
    private BigDecimal currBal = BigDecimal.ZERO.setScale(2);
    private BigDecimal creditLimit = BigDecimal.ZERO.setScale(2);
    private BigDecimal cashCreditLimit = BigDecimal.ZERO.setScale(2);
    private String openDate = "";
    private String expirationDate = "";
    private String reissueDate = "";
    private BigDecimal currCycCredit = BigDecimal.ZERO.setScale(2);
    private BigDecimal currCycDebit = BigDecimal.ZERO.setScale(2);
    private String addrZip = "";
    private String groupId = "";

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
        this.currBal = Cobol.amount(currBal, 10, 2);
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public void setCreditLimit(BigDecimal creditLimit) {
        this.creditLimit = Cobol.amount(creditLimit, 10, 2);
    }

    public BigDecimal getCashCreditLimit() {
        return cashCreditLimit;
    }

    public void setCashCreditLimit(BigDecimal cashCreditLimit) {
        this.cashCreditLimit = Cobol.amount(cashCreditLimit, 10, 2);
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
        this.currCycCredit = Cobol.amount(currCycCredit, 10, 2);
    }

    public BigDecimal getCurrCycDebit() {
        return currCycDebit;
    }

    public void setCurrCycDebit(BigDecimal currCycDebit) {
        this.currCycDebit = Cobol.amount(currCycDebit, 10, 2);
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
