package com.carddemo.cbtrn02c.service;

import com.carddemo.cbtrn02c.model.TransactionRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Accumulates the results of a CBTRN02C run: the posted transaction records, the
 * reject records, and the counters/return code reported at end of job.
 */
public final class BatchSummary {

    private final List<TransactionRecord> postedTransactions = new ArrayList<>();
    private final List<String> rejectRecords = new ArrayList<>();
    private long transactionCount;
    private long rejectCount;

    void recordProcessed() {
        transactionCount++;
    }

    void addPosted(TransactionRecord tran) {
        postedTransactions.add(tran);
    }

    void addReject(String rejectRecord) {
        rejectCount++;
        rejectRecords.add(rejectRecord);
    }

    public long getTransactionCount() {
        return transactionCount;
    }

    public long getRejectCount() {
        return rejectCount;
    }

    public List<TransactionRecord> getPostedTransactions() {
        return Collections.unmodifiableList(postedTransactions);
    }

    public List<String> getRejectRecords() {
        return Collections.unmodifiableList(rejectRecords);
    }

    /** RETURN-CODE is set to 4 when any transaction was rejected, otherwise 0. */
    public int getReturnCode() {
        return rejectCount > 0 ? 4 : 0;
    }
}
