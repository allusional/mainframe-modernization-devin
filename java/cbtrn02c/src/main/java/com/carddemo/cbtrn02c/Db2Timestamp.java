package com.carddemo.cbtrn02c;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.function.Supplier;

/**
 * Reproduces the COBOL paragraph Z-GET-DB2-FORMAT-TIMESTAMP, which formats
 * FUNCTION CURRENT-DATE as the 26 byte Db2 timestamp {@code EEEE-MM-DD-UU.MM.SS.HH0000}
 * (hundredths of a second followed by four literal zeros).
 */
public final class Db2Timestamp {

    private Db2Timestamp() {
    }

    public static String format(LocalDateTime moment) {
        int hundredths = moment.getNano() / 10_000_000;
        return String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000",
                moment.getYear(), moment.getMonthValue(), moment.getDayOfMonth(),
                moment.getHour(), moment.getMinute(), moment.getSecond(), hundredths);
    }

    /** A supplier that re-reads the clock for every posted transaction, like the COBOL program. */
    public static Supplier<String> fromClock(Clock clock) {
        return () -> format(LocalDateTime.now(clock));
    }

    /** A deterministic supplier, for reproducible runs and parity comparison. */
    public static Supplier<String> fixed(String timestamp) {
        return () -> timestamp;
    }

    /**
     * Parses a pinned run time in the GnuCOBOL {@code COB_CURRENT_DATE} format
     * {@code YYYYMMDDHHMMSS} or {@code YYYYMMDDHHMMSShh} into a Db2 format timestamp.
     */
    public static String fromCobCurrentDate(String cobCurrentDate) {
        String digits = cobCurrentDate.replace("-", "").replace(".", "").replace(":", "").replace(" ", "");
        if (digits.length() != 14 && digits.length() != 16) {
            throw new IllegalArgumentException("expected YYYYMMDDHHMMSS[hh], got: " + cobCurrentDate);
        }
        String hundredths = digits.length() == 16 ? digits.substring(14, 16) : "00";
        return digits.substring(0, 4) + "-" + digits.substring(4, 6) + "-" + digits.substring(6, 8) + "-"
                + digits.substring(8, 10) + "." + digits.substring(10, 12) + "." + digits.substring(12, 14) + "."
                + hundredths + "0000";
    }
}
