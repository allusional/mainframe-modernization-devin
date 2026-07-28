package com.carddemo.cbtrn02c.repository;

import com.carddemo.cbtrn02c.model.CardXrefRecord;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory equivalent of the indexed XREF-FILE (RANDOM access, keyed by card number).
 */
public class CardXrefRepository {

    private final Map<String, CardXrefRecord> byCardNumber = new LinkedHashMap<>();

    public void put(CardXrefRecord record) {
        byCardNumber.put(record.getCardNumber(), record);
    }

    /** Mirrors READ XREF-FILE ... INVALID KEY (returns empty when the key is absent). */
    public Optional<CardXrefRecord> findByCardNumber(String cardNumber) {
        return Optional.ofNullable(byCardNumber.get(cardNumber));
    }

    public int size() {
        return byCardNumber.size();
    }
}
