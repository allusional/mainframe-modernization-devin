package com.carddemo.posttran;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory stand-in for the TCATBALF VSAM file. */
public class InMemoryTranCatBalanceRepository implements TranCatBalanceRepository {

    private final Map<TranCatBalanceKey, TranCatBalance> byKey = new HashMap<>();

    @Override
    public Optional<TranCatBalance> find(TranCatBalanceKey key) {
        return Optional.ofNullable(byKey.get(key));
    }

    @Override
    public void create(TranCatBalance record) {
        byKey.put(record.key(), record);
    }

    @Override
    public void update(TranCatBalance record) {
        byKey.put(record.key(), record);
    }
}
