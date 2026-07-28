package com.carddemo.cbtrn02c.service;

import com.carddemo.cbtrn02c.model.DailyTransactionRecord;

import java.util.List;

/**
 * Orchestrates the CBTRN02C main loop (PROCEDURE DIVISION): read each daily
 * transaction, validate it, then post or reject, tracking the counters.
 */
public class DailyTransactionPostingJob {

    private final TransactionPostingService postingService;

    public DailyTransactionPostingJob(TransactionPostingService postingService) {
        this.postingService = postingService;
    }

    public BatchSummary run(List<DailyTransactionRecord> dailyTransactions) {
        BatchSummary summary = new BatchSummary();
        for (DailyTransactionRecord daly : dailyTransactions) {
            summary.recordProcessed();
            PostingOutcome outcome = postingService.process(daly);
            if (outcome.isPosted()) {
                summary.addPosted(outcome.getPostedTransaction());
            } else {
                summary.addReject(outcome.toRejectRecord());
            }
        }
        return summary;
    }
}
