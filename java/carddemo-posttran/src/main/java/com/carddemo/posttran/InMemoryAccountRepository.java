package com.carddemo.posttran;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory stand-in for the ACCTDAT VSAM file. */
public class InMemoryAccountRepository implements AccountRepository {

    private final Map<Long, Account> byAcctId = new HashMap<>();

    public void put(Account account) {
        byAcctId.put(account.getAcctId(), account);
    }

    @Override
    public Optional<Account> findById(long acctId) {
        return Optional.ofNullable(byAcctId.get(acctId));
    }

    @Override
    public void update(Account account) {
        byAcctId.put(account.getAcctId(), account);
    }
}
