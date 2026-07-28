package com.aws.carddemo.cbact04c.service;

/** Summary of an interest-calculation run (mirrors WS-COUNTERS). */
public class InterestCalculationResult {

    private final long recordCount;
    private final long transactionsWritten;

    public InterestCalculationResult(long recordCount, long transactionsWritten) {
        this.recordCount = recordCount;
        this.transactionsWritten = transactionsWritten;
    }

    /** Number of Transaction Category Balance records processed (WS-RECORD-COUNT). */
    public long getRecordCount() {
        return recordCount;
    }

    /** Number of interest transactions written (WS-TRANID-SUFFIX). */
    public long getTransactionsWritten() {
        return transactionsWritten;
    }
}
