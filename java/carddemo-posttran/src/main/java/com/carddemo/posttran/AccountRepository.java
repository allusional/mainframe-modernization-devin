package com.carddemo.posttran;

import java.util.Optional;

/** ACCOUNT-FILE, keyed by account id (1500-B-LOOKUP-ACCT, 2800-UPDATE-ACCOUNT-REC). */
public interface AccountRepository {

    Optional<Account> findById(long acctId);

    void update(Account account);
}
