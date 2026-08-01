package com.carddemo.intcalc;

import java.util.Optional;

/**
 * ACCTFILE: the account master KSDS, opened I-O. CBACT04C reads a record randomly by account id
 * ({@code 1100-GET-ACCT-DATA}) and rewrites it once the interest of the account is totalled
 * ({@code 1050-UPDATE-ACCOUNT}).
 */
public interface AccountRepository {

    Optional<Account> find(long acctId);

    void rewrite(Account account);
}
