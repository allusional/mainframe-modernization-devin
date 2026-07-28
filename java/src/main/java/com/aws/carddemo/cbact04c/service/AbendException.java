package com.aws.carddemo.cbact04c.service;

/**
 * Raised for the unrecoverable I/O conditions that make the original COBOL
 * program abend via 9999-ABEND-PROGRAM (CALL 'CEE3ABD'). This replaces the
 * mainframe abend with an explicit, catchable Java exception.
 */
public class AbendException extends RuntimeException {

    public AbendException(String message) {
        super(message);
    }
}
