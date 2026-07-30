package com.carddemo.intcalc;

/** CARD-XREF-RECORD (app/cpy/CVACT03Y.cpy), read through the XREFFILE alternate account id key. */
public record CardXref(String cardNum, String custId, long acctId) {
}
