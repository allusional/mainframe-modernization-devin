package com.carddemo.cbtrn02c.service;

/**
 * Outcome of transaction validation (paragraph 1500-VALIDATE-TRAN). Mirrors the COBOL
 * fields WS-VALIDATION-FAIL-REASON (PIC 9(04)) and WS-VALIDATION-FAIL-REASON-DESC
 * (PIC X(76)). A reason of 0 means the transaction is valid.
 */
public final class ValidationResult {

    public static final int OK = 0;
    public static final int INVALID_CARD_NUMBER = 100;
    public static final int ACCOUNT_NOT_FOUND = 101;
    public static final int OVERLIMIT = 102;
    public static final int AFTER_EXPIRATION = 103;

    private final int reasonCode;
    private final String reasonDescription;

    private ValidationResult(int reasonCode, String reasonDescription) {
        this.reasonCode = reasonCode;
        this.reasonDescription = reasonDescription;
    }

    public static ValidationResult ok() {
        return new ValidationResult(OK, "");
    }

    public static ValidationResult reject(int reasonCode, String reasonDescription) {
        return new ValidationResult(reasonCode, reasonDescription);
    }

    public boolean isValid() {
        return reasonCode == OK;
    }

    public int getReasonCode() {
        return reasonCode;
    }

    public String getReasonDescription() {
        return reasonDescription;
    }

    /**
     * Builds the 80-byte VALIDATION-TRAILER: a 4-digit reason code followed by a
     * 76-byte space-padded description.
     */
    public String toTrailer() {
        String code = String.format("%04d", reasonCode);
        String desc = reasonDescription == null ? "" : reasonDescription;
        if (desc.length() > 76) {
            desc = desc.substring(0, 76);
        } else {
            desc = desc + " ".repeat(76 - desc.length());
        }
        return code + desc;
    }
}
