package com.carddemo.cbtrn02c.copybook;

/**
 * DALYREJS record written by 2500-WRITE-REJECT-REC, RECLN 430.
 *
 * <pre>
 * 05 REJECT-TRAN-DATA                   PIC X(350)  offset   0  (the DALYTRAN record verbatim)
 * 05 VALIDATION-TRAILER                 PIC X(80)   offset 350
 *    05 WS-VALIDATION-FAIL-REASON       PIC 9(04)   offset 350
 *    05 WS-VALIDATION-FAIL-REASON-DESC  PIC X(76)   offset 354
 * </pre>
 */
public record RejectRecord(String tranData, int failReason, String failReasonDescription) {

    public static final int LENGTH = 430;

    public static RejectRecord parse(String raw) {
        if (raw.length() != LENGTH) {
            throw new IllegalArgumentException("REJECT record must be " + LENGTH + " bytes, got " + raw.length());
        }
        return new RejectRecord(
                CobolField.alpha(raw, 0, 350),
                Integer.parseInt(CobolField.digits(raw, 350, 4)),
                CobolField.alpha(raw, 354, 76));
    }

    public String serialize() {
        return CobolField.moveAlpha(tranData, 350)
                + String.format("%04d", failReason)
                + CobolField.moveAlpha(failReasonDescription, 76);
    }
}
