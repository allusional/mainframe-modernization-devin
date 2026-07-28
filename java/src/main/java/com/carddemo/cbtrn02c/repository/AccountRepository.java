package com.carddemo.cbtrn02c.repository;

import com.carddemo.cbtrn02c.model.AccountRecord;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory equivalent of the indexed ACCOUNT-FILE (opened I-O, keyed by account id).
 */
public class AccountRepository {

    private final Map<Long, AccountRecord> byId = new LinkedHashMap<>();

    public void put(AccountRecord record) {
        byId.put(record.getAccountId(), record);
    }

    /** Mirrors READ ACCOUNT-FILE ... INVALID KEY. */
    public Optional<AccountRecord> findById(long accountId) {
        return Optional.ofNullable(byId.get(accountId));
    }

    /**
     * Mirrors REWRITE FD-ACCTFILE-REC. Records are mutated in place, so this signals
     * an invalid key only when the account is unknown.
     */
    public boolean rewrite(AccountRecord record) {
        if (!byId.containsKey(record.getAccountId())) {
            return false;
        }
        byId.put(record.getAccountId(), record);
        return true;
    }
}
