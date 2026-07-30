package com.carddemo.posttran.files;

import com.carddemo.posttran.Transaction;
import com.carddemo.posttran.TransactionWriter;
import java.util.List;
import java.util.TreeMap;

/** TRANFILE: KSDS keyed on TRAN-ID, written by 2900-WRITE-TRANSACTION-FILE. */
public class FlatFileTransactionWriter implements TransactionWriter {

    private final TreeMap<String, Transaction> byId = new TreeMap<>();

    @Override
    public void write(Transaction transaction) {
        byId.put(Cobol.putText(transaction.getId(), 16), transaction);
    }

    /** The file in key sequence, as a KSDS is read back. */
    public List<String> records() {
        return byId.values().stream().map(Layouts::transaction).toList();
    }
}
