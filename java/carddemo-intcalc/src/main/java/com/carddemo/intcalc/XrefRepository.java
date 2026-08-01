package com.carddemo.intcalc;

import java.util.Optional;

/**
 * XREFFILE: the card cross reference KSDS, read randomly through its ALTERNATE RECORD KEY
 * {@code FD-XREF-ACCT-ID} ({@code 1110-GET-XREF-DATA}).
 */
public interface XrefRepository {

    Optional<CardXref> findByAcctId(long acctId);
}
