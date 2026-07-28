package com.carddemo.cbtrn02c.repository;

import com.carddemo.cbtrn02c.model.TranCatBalRecord;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory equivalent of the indexed TCATBAL-FILE (opened I-O, keyed by
 * account id + type code + category code).
 */
public class TranCatBalRepository {

    private final Map<String, TranCatBalRecord> byKey = new LinkedHashMap<>();

    public void put(TranCatBalRecord record) {
        byKey.put(record.getKey(), record);
    }

    /** Mirrors READ TCATBAL-FILE ... INVALID KEY. */
    public Optional<TranCatBalRecord> findByKey(String key) {
        return Optional.ofNullable(byKey.get(key));
    }

    /** Mirrors WRITE (create) and REWRITE (update) of FD-TRAN-CAT-BAL-RECORD. */
    public void save(TranCatBalRecord record) {
        byKey.put(record.getKey(), record);
    }
}
