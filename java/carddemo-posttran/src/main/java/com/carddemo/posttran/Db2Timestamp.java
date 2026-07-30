package com.carddemo.posttran;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Z-GET-DB2-FORMAT-TIMESTAMP: 26-char DB2 timestamp YYYY-MM-DD-HH.MM.SS.hh0000. */
public class Db2Timestamp {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SS", Locale.ROOT);

    private final Clock clock;

    public Db2Timestamp(Clock clock) {
        this.clock = clock;
    }

    /**
     * COBOL keeps only the hundredths of a second reported by FUNCTION CURRENT-DATE (COB-MIL),
     * then pads the remaining four digits with zeroes.
     */
    public String now() {
        return LocalDateTime.now(clock).format(FORMAT) + "0000";
    }
}
