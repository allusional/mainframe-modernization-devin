package com.carddemo.intcalc.files;

import com.carddemo.intcalc.TranCatBalance;
import com.carddemo.intcalc.TranCatBalanceReader;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

/** TCATBALF: a KSDS read sequentially, so the records come back in TRAN-CAT-KEY sequence. */
public class FlatFileTranCatBalanceReader implements TranCatBalanceReader {

    private final List<TranCatBalance> records;
    private int position;

    public FlatFileTranCatBalanceReader(List<String> images) {
        TreeMap<String, TranCatBalance> byKey = new TreeMap<>();
        for (String image : images) {
            TranCatBalance balance = Layouts.tranCatBalance(image);
            byKey.put(image.substring(0, 17), balance);
        }
        this.records = List.copyOf(byKey.values());
    }

    @Override
    public Optional<TranCatBalance> next() {
        if (position >= records.size()) {
            return Optional.empty();
        }
        return Optional.of(records.get(position++));
    }

    public List<TranCatBalance> records() {
        return records;
    }
}
