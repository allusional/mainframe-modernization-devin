package com.carddemo.posttran.files;

import com.carddemo.posttran.DailyTransaction;
import com.carddemo.posttran.DailyTransactionReader;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/** DALYTRAN: sequential input read record by record (1000-DALYTRAN-GET-NEXT). */
public class FlatFileDailyTransactionReader implements DailyTransactionReader {

    private final Iterator<String> records;

    public FlatFileDailyTransactionReader(List<String> records) {
        this.records = records.iterator();
    }

    @Override
    public Optional<DailyTransaction> next() {
        if (!records.hasNext()) {
            return Optional.empty();
        }
        return Optional.of(Layouts.dailyTransaction(records.next()));
    }
}
