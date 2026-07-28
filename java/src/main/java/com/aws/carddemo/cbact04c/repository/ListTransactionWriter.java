package com.aws.carddemo.cbact04c.repository;

import com.aws.carddemo.cbact04c.model.TransactionRecord;

import java.util.ArrayList;
import java.util.List;

/** Collects written transactions in memory, primarily for tests and reporting. */
public class ListTransactionWriter implements TransactionWriter {

    private final List<TransactionRecord> written = new ArrayList<>();

    @Override
    public void write(TransactionRecord transaction) {
        written.add(transaction);
    }

    public List<TransactionRecord> getWritten() {
        return written;
    }
}
