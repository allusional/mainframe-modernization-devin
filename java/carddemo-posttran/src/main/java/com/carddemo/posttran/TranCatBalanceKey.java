package com.carddemo.posttran;

/** TRAN-CAT-KEY: composite key of the transaction category balance file. */
public record TranCatBalanceKey(long acctId, String typeCd, String catCd) {
}
