package com.carddemo.intcalc;

import java.util.Optional;

/**
 * TCATBALF: the VSAM KSDS CBACT04C opens INPUT and reads in key sequence
 * ({@code 1000-TCATBALF-GET-NEXT}). An empty {@code Optional} is the AT END / status '10' case.
 */
public interface TranCatBalanceReader {

    Optional<TranCatBalance> next();
}
