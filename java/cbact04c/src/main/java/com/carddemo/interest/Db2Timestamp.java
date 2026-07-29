package com.carddemo.interest;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Z-GET-DB2-FORMAT-TIMESTAMP: {@code yyyy-MM-dd-HH.mm.ss.hh0000}, where {@code hh} is
 * hundredths of a second (COBOL's FUNCTION CURRENT-DATE resolution) and the remaining four
 * microsecond digits are hard coded zeroes, exactly as the COBOL does.
 */
public final class Db2Timestamp {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss");

    private Db2Timestamp() {
    }

    public static String now(Clock clock) {
        LocalDateTime now = LocalDateTime.now(clock);
        int hundredths = now.getNano() / 10_000_000;
        return FORMAT.format(now) + "." + String.format("%02d", hundredths) + "0000";
    }
}
