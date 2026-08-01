package com.carddemo.intcalc;

import java.math.BigDecimal;

/**
 * DIS-GROUP-RECORD (app/cpy/CVTRA02Y.cpy), the DISCGRP record holding the interest rate for an
 * account group / transaction type / transaction category. {@code DIS-INT-RATE} is
 * {@code PIC S9(04)V99}, an annual percentage.
 */
public record DiscGroup(DiscGroupKey key, BigDecimal intRate) {

    public DiscGroup(DiscGroupKey key, BigDecimal intRate) {
        this.key = key;
        this.intRate = Cobol.amount(intRate, 4, 2);
    }
}
