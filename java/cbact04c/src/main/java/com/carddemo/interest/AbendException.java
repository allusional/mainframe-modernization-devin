package com.carddemo.interest;

/** Equivalent of 9999-ABEND-PROGRAM: the job stops, nothing downstream is produced. */
public class AbendException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public static final int ABEND_CODE = 999;

    public AbendException(String message) {
        super(message);
    }

    public AbendException(String message, Throwable cause) {
        super(message, cause);
    }
}
