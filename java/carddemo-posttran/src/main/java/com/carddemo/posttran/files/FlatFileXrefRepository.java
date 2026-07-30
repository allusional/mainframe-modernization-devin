package com.carddemo.posttran.files;

import com.carddemo.posttran.CardXref;
import com.carddemo.posttran.XrefRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** XREFFILE: read-only KSDS keyed on XREF-CARD-NUM. */
public class FlatFileXrefRepository implements XrefRepository {

    private final Map<String, CardXref> byCardNum = new LinkedHashMap<>();

    public FlatFileXrefRepository(List<String> records) {
        for (String record : records) {
            CardXref xref = Layouts.xref(record);
            byCardNum.put(xref.getCardNum(), xref);
        }
    }

    @Override
    public Optional<CardXref> findByCardNum(String cardNum) {
        return Optional.ofNullable(byCardNum.get(cardNum));
    }
}
