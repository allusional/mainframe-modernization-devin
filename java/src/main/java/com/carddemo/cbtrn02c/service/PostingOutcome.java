package com.carddemo.cbtrn02c.service;

import com.carddemo.cbtrn02c.model.DailyTransactionRecord;
import com.carddemo.cbtrn02c.model.TransactionRecord;

/**
 * Result of processing a single daily transaction: either posted (a transaction
 * record was produced and balances updated) or rejected (with a validation reason).
 */
public final class PostingOutcome {

    private final DailyTransactionRecord source;
    private final ValidationResult validation;
    private final TransactionRecord postedTransaction;

    private PostingOutcome(DailyTransactionRecord source, ValidationResult validation,
                           TransactionRecord postedTransaction) {
        this.source = source;
        this.validation = validation;
        this.postedTransaction = postedTransaction;
    }

    public static PostingOutcome posted(DailyTransactionRecord source, TransactionRecord tran) {
        return new PostingOutcome(source, ValidationResult.ok(), tran);
    }

    public static PostingOutcome rejected(DailyTransactionRecord source, ValidationResult validation) {
        return new PostingOutcome(source, validation, null);
    }

    public boolean isPosted() {
        return postedTransaction != null;
    }

    public boolean isRejected() {
        return postedTransaction == null;
    }

    public ValidationResult getValidation() {
        return validation;
    }

    public TransactionRecord getPostedTransaction() {
        return postedTransaction;
    }

    /**
     * Builds the 430-byte reject record written to DALYREJS: the original 350-byte
     * transaction data followed by the 80-byte validation trailer.
     */
    public String toRejectRecord() {
        return source.getRawRecord() + validation.toTrailer();
    }
}
