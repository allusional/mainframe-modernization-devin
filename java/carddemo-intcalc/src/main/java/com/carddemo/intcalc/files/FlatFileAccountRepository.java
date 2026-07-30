package com.carddemo.intcalc.files;

import com.carddemo.intcalc.Account;
import com.carddemo.intcalc.AccountRepository;
import com.carddemo.intcalc.Cobol;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * ACCTFILE: a KSDS opened I-O and keyed on ACCT-ID, so a dump of the file comes back in key
 * sequence. The 178 byte FILLER of every record is kept as it was read, which is what a COBOL
 * {@code READ INTO} followed by {@code REWRITE FROM} leaves on the file.
 */
public class FlatFileAccountRepository implements AccountRepository {

    private final TreeMap<String, Account> byId = new TreeMap<>();
    private final Map<String, String> fillers = new HashMap<>();
    private int rewrites;

    public FlatFileAccountRepository(List<String> images) {
        for (String image : images) {
            Account account = Layouts.account(image);
            byId.put(key(account.getAcctId()), account);
            fillers.put(key(account.getAcctId()), Layouts.accountFiller(image));
        }
    }

    @Override
    public Optional<Account> find(long acctId) {
        return Optional.ofNullable(byId.get(key(acctId)));
    }

    @Override
    public void rewrite(Account account) {
        byId.put(key(account.getAcctId()), account);
        rewrites++;
    }

    /** The file in key sequence, as a KSDS is read back. */
    public List<String> records() {
        return byId.entrySet().stream()
                .map(entry -> Layouts.account(entry.getValue(), fillers.get(entry.getKey())))
                .toList();
    }

    /** How many times 1050-UPDATE-ACCOUNT rewrote a record. */
    public int rewriteCount() {
        return rewrites;
    }

    private static String key(long acctId) {
        return Cobol.putDigits(acctId, 11);
    }
}
