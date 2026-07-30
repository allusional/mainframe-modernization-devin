package com.carddemo.intcalc;

import java.util.ArrayList;
import java.util.List;

/** In-memory stand-in for the TRANSACT sequential file. */
public class InMemoryTransactionWriter implements TransactionWriter {

    private final List<Transaction> written = new ArrayList<>();

    @Override
    public void write(Transaction transaction) {
        written.add(transaction);
    }

    public List<Transaction> written() {
        return List.copyOf(written);
    }
}
