package com.carddemo.posttran.files;

import com.carddemo.posttran.Account;
import com.carddemo.posttran.AccountRepository;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

/** ACCTFILE: KSDS opened I-O, keyed on ACCT-ID; records are rewritten in place. */
public class FlatFileAccountRepository implements AccountRepository {

    private final TreeMap<Long, Account> byId = new TreeMap<>();

    public FlatFileAccountRepository(List<String> records) {
        for (String record : records) {
            Account account = Layouts.account(record);
            byId.put(account.getAcctId(), account);
        }
    }

    @Override
    public Optional<Account> findById(long acctId) {
        return Optional.ofNullable(byId.get(acctId));
    }

    @Override
    public void update(Account account) {
        byId.put(account.getAcctId(), account);
    }

    /** The file in key sequence, as a KSDS is read back. */
    public List<String> records() {
        return byId.values().stream().map(Layouts::account).toList();
    }
}
