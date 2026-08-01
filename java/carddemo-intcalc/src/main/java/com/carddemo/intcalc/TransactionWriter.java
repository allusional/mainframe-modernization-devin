package com.carddemo.intcalc;

/** TRANSACT: the sequential (QSAM) transaction file CBACT04C opens OUTPUT and writes. */
public interface TransactionWriter {

    void write(Transaction transaction);
}
