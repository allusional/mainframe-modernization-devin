package com.aws.carddemo.cbact04c.repository;

import com.aws.carddemo.cbact04c.model.CardXrefRecord;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Map-backed {@link CardXrefRepository} keyed by account id. */
public class InMemoryCardXrefRepository implements CardXrefRepository {

    private final Map<Long, CardXrefRecord> byAccountId = new HashMap<>();

    public void put(CardXrefRecord xref) {
        byAccountId.putIfAbsent(xref.getAccountId(), xref);
    }

    @Override
    public Optional<CardXrefRecord> readByAccountId(long accountId) {
        return Optional.ofNullable(byAccountId.get(accountId));
    }
}
