package com.carddemo.intcalc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** In-memory stand-in for the TCATBALF VSAM file, read in the order it is given. */
public class InMemoryTranCatBalanceReader implements TranCatBalanceReader {

    private final List<TranCatBalance> records;
    private int position;

    public InMemoryTranCatBalanceReader(List<TranCatBalance> records) {
        this.records = new ArrayList<>(records);
    }

    @Override
    public Optional<TranCatBalance> next() {
        if (position >= records.size()) {
            return Optional.empty();
        }
        return Optional.of(records.get(position++));
    }
}
