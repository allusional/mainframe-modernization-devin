package com.carddemo.posttran;

import java.util.Optional;

/** DALYTRAN-FILE, sequential input (1000-DALYTRAN-GET-NEXT). */
public interface DailyTransactionReader {

    /** Empty when end-of-file is reached. */
    Optional<DailyTransaction> next();
}
