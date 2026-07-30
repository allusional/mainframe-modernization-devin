package com.carddemo.intcalc.files;

import com.carddemo.intcalc.DiscGroup;
import com.carddemo.intcalc.DiscGroupKey;
import com.carddemo.intcalc.DiscGroupRepository;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

/** DISCGRP: a KSDS keyed on account group id + transaction type + transaction category. */
public class FlatFileDiscGroupRepository implements DiscGroupRepository {

    private final TreeMap<String, DiscGroup> byKey = new TreeMap<>();

    public FlatFileDiscGroupRepository(List<String> images) {
        for (String image : images) {
            DiscGroup group = Layouts.discGroup(image);
            byKey.put(group.key().image(), group);
        }
    }

    @Override
    public Optional<DiscGroup> find(DiscGroupKey key) {
        return Optional.ofNullable(byKey.get(key.image()));
    }
}
