package com.aws.carddemo.cbact04c.repository;

import com.aws.carddemo.cbact04c.model.TransactionRecord;

/** Sequential writer for the generated interest Transaction file (TRANSACT). */
public interface TransactionWriter {

    void write(TransactionRecord transaction);
}
