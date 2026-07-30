package com.carddemo.intcalc.files;

import com.carddemo.intcalc.Transaction;
import com.carddemo.intcalc.TransactionWriter;
import java.util.ArrayList;
import java.util.List;

/** TRANSACT: a record SEQUENTIAL (QSAM) file, written in the order the transactions are created. */
public class FlatFileTransactionWriter implements TransactionWriter {

    private final List<String> records = new ArrayList<>();

    @Override
    public void write(Transaction transaction) {
        records.add(Layouts.transaction(transaction));
    }

    public List<String> records() {
        return List.copyOf(records);
    }
}
