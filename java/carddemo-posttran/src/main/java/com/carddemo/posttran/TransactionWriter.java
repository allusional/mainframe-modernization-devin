package com.carddemo.posttran;

/** TRANSACT-FILE output (2900-WRITE-TRANSACTION-FILE). */
public interface TransactionWriter {

    void write(Transaction transaction);
}
