package com.aws.carddemo.cbact04c.repository;

import com.aws.carddemo.cbact04c.model.AccountRecord;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Map-backed {@link AccountRepository} used for file-loaded data and tests. */
public class InMemoryAccountRepository implements AccountRepository {

    private final Map<Long, AccountRecord> accounts = new LinkedHashMap<>();

    public void put(AccountRecord account) {
        accounts.put(account.getAccountId(), account);
    }

    @Override
    public Optional<AccountRecord> read(long accountId) {
        return Optional.ofNullable(accounts.get(accountId));
    }

    @Override
    public void rewrite(AccountRecord account) {
        accounts.put(account.getAccountId(), account);
    }

    public Map<Long, AccountRecord> asMap() {
        return accounts;
    }
}
