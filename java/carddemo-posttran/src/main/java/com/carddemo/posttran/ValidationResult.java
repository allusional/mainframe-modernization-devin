package com.carddemo.posttran;

/** WS-VALIDATION-TRAILER: fail reason code and description set by 1500-VALIDATE-TRAN. */
public final class ValidationResult {

    static final ValidationResult OK = new ValidationResult(0, "", null, null);

    private final int failReason;
    private final String failReasonDesc;
    private final CardXref xref;
    private final Account account;

    ValidationResult(int failReason, String failReasonDesc, CardXref xref, Account account) {
        this.failReason = failReason;
        this.failReasonDesc = failReasonDesc;
        this.xref = xref;
        this.account = account;
    }

    public int getFailReason() {
        return failReason;
    }

    public String getFailReasonDesc() {
        return failReasonDesc;
    }

    public CardXref getXref() {
        return xref;
    }

    public Account getAccount() {
        return account;
    }

    public boolean isValid() {
        return failReason == 0;
    }
}
