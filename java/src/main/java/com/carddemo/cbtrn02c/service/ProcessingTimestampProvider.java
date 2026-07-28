package com.carddemo.cbtrn02c.service;

import java.time.LocalDateTime;

/**
 * Supplies the DB2-format processing timestamp assigned to TRAN-PROC-TS,
 * replicating paragraph Z-GET-DB2-FORMAT-TIMESTAMP.
 *
 * <p>Layout (26 bytes): {@code YYYY-MM-DD-HH.MM.SS.hh0000} where {@code hh} is
 * hundredths of a second and the trailing {@code 0000} is the literal DB2-REST filler.
 */
@FunctionalInterface
public interface ProcessingTimestampProvider {

    String currentTimestamp();

    static String format(LocalDateTime dt) {
        int hundredths = dt.getNano() / 10_000_000;
        return String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000",
                dt.getYear(), dt.getMonthValue(), dt.getDayOfMonth(),
                dt.getHour(), dt.getMinute(), dt.getSecond(), hundredths);
    }

    static ProcessingTimestampProvider systemClock() {
        return () -> format(LocalDateTime.now());
    }
}
