package com.carddemo.intcalc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory stand-in for the DISCGRP VSAM file. */
public class InMemoryDiscGroupRepository implements DiscGroupRepository {

    private final Map<String, DiscGroup> byKey = new HashMap<>();

    public InMemoryDiscGroupRepository(List<DiscGroup> records) {
        for (DiscGroup record : records) {
            byKey.put(record.key().image(), record);
        }
    }

    @Override
    public Optional<DiscGroup> find(DiscGroupKey key) {
        return Optional.ofNullable(byKey.get(key.image()));
    }
}
