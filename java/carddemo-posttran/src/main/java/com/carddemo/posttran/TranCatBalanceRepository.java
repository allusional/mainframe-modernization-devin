package com.carddemo.posttran;

import java.util.Optional;

/** TCATBAL-FILE, keyed by (acctId, typeCd, catCd) (2700-UPDATE-TCATBAL). */
public interface TranCatBalanceRepository {

    Optional<TranCatBalance> find(TranCatBalanceKey key);

    void create(TranCatBalance record);

    void update(TranCatBalance record);
}
