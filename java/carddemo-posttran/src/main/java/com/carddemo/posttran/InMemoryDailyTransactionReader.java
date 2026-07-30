package com.carddemo.posttran;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Optional;

/** In-memory stand-in for the sequential DALYTRAN file. */
public class InMemoryDailyTransactionReader implements DailyTransactionReader {

    private final Deque<DailyTransaction> pending;

    public InMemoryDailyTransactionReader(Collection<DailyTransaction> records) {
        this.pending = new ArrayDeque<>(records);
    }

    @Override
    public Optional<DailyTransaction> next() {
        return Optional.ofNullable(pending.poll());
    }
}
