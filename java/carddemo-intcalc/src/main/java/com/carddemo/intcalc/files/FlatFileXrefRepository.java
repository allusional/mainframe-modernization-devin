package com.carddemo.intcalc.files;

import com.carddemo.intcalc.CardXref;
import com.carddemo.intcalc.XrefRepository;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

/**
 * XREFFILE: a KSDS keyed on XREF-CARD-NUM with an alternate index on XREF-ACCT-ID. A random read
 * on the alternate key returns the first record of the index, so the records are held in card
 * number sequence and the first match wins, as VSAM does for a non-unique alternate key.
 */
public class FlatFileXrefRepository implements XrefRepository {

    private final TreeMap<String, CardXref> byCardNum = new TreeMap<>();

    public FlatFileXrefRepository(List<String> images) {
        for (String image : images) {
            CardXref xref = Layouts.xref(image);
            byCardNum.put(xref.cardNum(), xref);
        }
    }

    @Override
    public Optional<CardXref> findByAcctId(long acctId) {
        return byCardNum.values().stream().filter(xref -> xref.acctId() == acctId).findFirst();
    }
}
