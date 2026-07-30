package com.carddemo.posttran;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Helpers for COBOL PIC semantics. */
final class Pic {

    private Pic() {
    }

    /** PIC S9(n)V99: signed decimal truncated to scale 2, like a COBOL MOVE into a V99 field. */
    static BigDecimal amount(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.DOWN);
    }
}
