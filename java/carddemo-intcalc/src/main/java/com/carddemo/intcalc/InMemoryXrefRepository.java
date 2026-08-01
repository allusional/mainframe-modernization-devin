package com.carddemo.intcalc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory stand-in for the XREFFILE VSAM file and its account id alternate index. */
public class InMemoryXrefRepository implements XrefRepository {

    private final Map<Long, CardXref> byAcctId = new HashMap<>();

    public InMemoryXrefRepository(List<CardXref> records) {
        for (CardXref record : records) {
            byAcctId.putIfAbsent(record.acctId(), record);
        }
    }

    @Override
    public Optional<CardXref> findByAcctId(long acctId) {
        return Optional.ofNullable(byAcctId.get(acctId));
    }
}
