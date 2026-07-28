package com.aws.carddemo.cbact04c.service;

import java.time.LocalDateTime;

/**
 * Supplies the 26-character DB2-format timestamp stamped onto generated
 * transactions, mirroring the Z-GET-DB2-FORMAT-TIMESTAMP paragraph:
 * {@code YYYY-MM-DD-HH.MM.SS.hh0000} where {@code hh} is hundredths of a second.
 * Abstracted behind an interface so tests can inject a deterministic value.
 */
public interface TimestampProvider {

    String currentTimestamp();

    /** Default provider based on the system clock. */
    class SystemClock implements TimestampProvider {
        @Override
        public String currentTimestamp() {
            return format(LocalDateTime.now());
        }
    }

    static String format(LocalDateTime now) {
        int hundredths = now.getNano() / 10_000_000;
        return String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(),
                now.getHour(), now.getMinute(), now.getSecond(), hundredths);
    }
}
