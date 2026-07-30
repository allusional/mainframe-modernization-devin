package com.carddemo.posttran;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory stand-in for the XREF VSAM file. */
public class InMemoryXrefRepository implements XrefRepository {

    private final Map<String, CardXref> byCardNum = new HashMap<>();

    public void put(CardXref xref) {
        byCardNum.put(xref.getCardNum(), xref);
    }

    @Override
    public Optional<CardXref> findByCardNum(String cardNum) {
        return Optional.ofNullable(byCardNum.get(cardNum));
    }
}
