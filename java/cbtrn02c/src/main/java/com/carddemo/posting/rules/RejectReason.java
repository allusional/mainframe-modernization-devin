package com.carddemo.posting.rules;

/**
 * Every reason CBTRN02C can refuse to post a transaction, with the code and the description
 * exactly as the COBOL writes them into the DALYREJS trailer.
 *
 * <p>The description strings are byte-for-byte copies of the literals in the source; they end
 * up space padded to 76 characters in the file because
 * {@code WS-VALIDATION-FAIL-REASON-DESC} is {@code PIC X(76)}.
 */
public enum RejectReason {

    /** CBTRN02C.cbl:385-387 - the card number is not in the cross reference file. */
    INVALID_CARD_NUMBER(100, "INVALID CARD NUMBER FOUND"),

    /** CBTRN02C.cbl:397-399 - the cross reference names an account that is not in the master. */
    ACCOUNT_NOT_FOUND(101, "ACCOUNT RECORD NOT FOUND"),

    /** CBTRN02C.cbl:410-412 - the transaction takes the account past its credit limit. */
    OVERLIMIT(102, "OVERLIMIT TRANSACTION"),

    /** CBTRN02C.cbl:417-419 - the transaction is dated after the account's expiry date. */
    AFTER_EXPIRATION(103, "TRANSACTION RECEIVED AFTER ACCT EXPIRATION"),

    /**
     * CBTRN02C.cbl:556-558 - the account rewrite failed. In the COBOL this code is set and
     * then never read (finding D1), so it can never reach the reject file. This port writes
     * it, unless {@code --emulate-lost-account-update} asks for the original behaviour.
     */
    ACCOUNT_REWRITE_FAILED(109, "ACCOUNT RECORD NOT FOUND"),

    /**
     * Not in the COBOL. CBTRN02C abends on a duplicate transaction id instead of rejecting
     * it (finding D2); this port rejects it. {@code --emulate-abend-on-duplicate-tran-id}
     * restores the abend.
     */
    DUPLICATE_TRANSACTION_ID(110, "DUPLICATE TRANSACTION ID");

    private final int code;
    private final String description;

    RejectReason(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /** The value of WS-VALIDATION-FAIL-REASON; written as four zero padded digits. */
    public int code() {
        return code;
    }

    public String description() {
        return description;
    }
}
