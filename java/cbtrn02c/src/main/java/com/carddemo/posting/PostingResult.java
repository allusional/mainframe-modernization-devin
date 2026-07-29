package com.carddemo.posting;

import com.carddemo.posting.rules.RejectReason;

import java.util.Collections;
import java.util.Map;

/**
 * What the job reports at the end: the two counters CBTRN02C displays, the return code it
 * sets, and a per-reason breakdown that the COBOL does not produce but which is derivable
 * from the reject file.
 *
 * @param transactionsProcessed WS-TRANSACTION-COUNT - records <em>read</em>, not records posted
 *                              (CBTRN02C.cbl:206, displayed at :227). See finding D10.
 * @param transactionsRejected  WS-REJECT-COUNT (CBTRN02C.cbl:214, displayed at :228).
 */
public record PostingResult(long transactionsProcessed, long transactionsRejected,
                            Map<RejectReason, Long> rejectsByReason, Map<String, Long> bucketsCreated) {

    public PostingResult {
        rejectsByReason = Collections.unmodifiableMap(rejectsByReason);
        bucketsCreated = Collections.unmodifiableMap(bucketsCreated);
    }

    public long transactionsPosted() {
        return transactionsProcessed - transactionsRejected;
    }

    /** CBTRN02C.cbl:229-231: RC=4 if anything was rejected, otherwise 0. */
    public int returnCode() {
        return transactionsRejected > 0 ? 4 : 0;
    }
}
