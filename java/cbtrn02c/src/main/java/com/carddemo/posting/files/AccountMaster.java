package com.carddemo.posting.files;

import com.carddemo.interest.records.AccountRecord;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The ACCTFILE KSDS, opened I-O: keyed reads and rewrites in place, unloaded in key order.
 *
 * <p>{@link #rewrite} returns false for the COBOL's {@code INVALID KEY} branch. That branch
 * drives finding D1, so it is a real, overridable outcome rather than an assumption - see
 * {@code AccountMasterWithFailingRewrite} in the tests.
 */
public class AccountMaster {

    private final Map<Long, AccountRecord> accounts = new TreeMap<>();

    public AccountMaster(Iterable<AccountRecord> records) {
        for (AccountRecord record : records) {
            accounts.put(record.accountId(), record);
        }
    }

    public Optional<AccountRecord> read(long accountId) {
        return Optional.ofNullable(accounts.get(accountId));
    }

    /** @return false if the record is no longer there, i.e. the COBOL's INVALID KEY. */
    public boolean rewrite(AccountRecord account) {
        if (!accounts.containsKey(account.accountId())) {
            return false;
        }
        accounts.put(account.accountId(), account);
        return true;
    }

    /** Every record in key order, which is how a sequential read of the KSDS returns them. */
    public Map<Long, AccountRecord> inKeyOrder() {
        return new LinkedHashMap<>(accounts);
    }
}
