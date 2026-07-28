package com.carddemo.cbtrn02c.service;

/**
 * Raised for unrecoverable I/O errors, mirroring paragraph 9999-ABEND-PROGRAM
 * (which issues CEE3ABD with abend code 999 in the COBOL program).
 */
public class BatchAbendException extends RuntimeException {

    public BatchAbendException(String message) {
        super(message);
    }
}
