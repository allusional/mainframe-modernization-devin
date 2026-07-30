package com.carddemo.intcalc;

/** FD-DISCGRP-KEY: account group id X(10) + transaction type code X(02) + category code 9(04). */
public record DiscGroupKey(String acctGroupId, String tranTypeCd, String tranCatCd) {

    /** The 16 byte key image, the way the COBOL RECORD KEY is compared on the file. */
    public String image() {
        return Cobol.putText(acctGroupId, 10) + Cobol.putText(tranTypeCd, 2) + Cobol.putDigits(tranCatCd, 4);
    }
}
