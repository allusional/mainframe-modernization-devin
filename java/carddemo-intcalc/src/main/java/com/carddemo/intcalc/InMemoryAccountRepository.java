package com.carddemo.intcalc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory stand-in for the ACCTFILE VSAM file. */
public class InMemoryAccountRepository implements AccountRepository {

    private final Map<Long, Account> byId = new LinkedHashMap<>();

    public InMemoryAccountRepository(List<Account> accounts) {
        for (Account account : accounts) {
            byId.put(account.getAcctId(), account);
        }
    }

    @Override
    public Optional<Account> find(long acctId) {
        return Optional.ofNullable(byId.get(acctId));
    }

    @Override
    public void rewrite(Account account) {
        byId.put(account.getAcctId(), account);
    }

    public List<Account> accounts() {
        return List.copyOf(byId.values());
    }
}
