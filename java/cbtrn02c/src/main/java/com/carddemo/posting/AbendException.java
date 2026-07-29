package com.carddemo.posting;

/** 9999-ABEND-PROGRAM: {@code CALL 'CEE3ABD' USING 999} (CBTRN02C.cbl:707-711). */
public class AbendException extends RuntimeException {

    public static final int ABEND_CODE = 999;

    public AbendException(String message) {
        super(message);
    }
}
