package com.carddemo.intcalc;

/**
 * 9999-ABEND-PROGRAM: {@code CALL 'CEE3ABD'} with abend code 999, which ends the job step. The
 * DISPLAY lines the COBOL writes before abending ({@code 9910-DISPLAY-IO-STATUS}) have already
 * been sent to the display sink when this is thrown.
 */
public class AbendException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int abendCode;

    public AbendException(String message, int abendCode) {
        super(message);
        this.abendCode = abendCode;
    }

    public int getAbendCode() {
        return abendCode;
    }
}
