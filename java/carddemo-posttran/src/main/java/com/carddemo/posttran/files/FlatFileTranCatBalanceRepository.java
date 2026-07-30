package com.carddemo.posttran.files;

import com.carddemo.posttran.TranCatBalance;
import com.carddemo.posttran.TranCatBalanceKey;
import com.carddemo.posttran.TranCatBalanceRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** TCATBALF: KSDS opened I-O, keyed on TRAN-CAT-KEY (acct id + type code + category code). */
public class FlatFileTranCatBalanceRepository implements TranCatBalanceRepository {

    private final TreeMap<String, TranCatBalance> byKey = new TreeMap<>();
    private final Map<String, String> fillers = new HashMap<>();

    public FlatFileTranCatBalanceRepository(List<String> records) {
        for (String record : records) {
            TranCatBalance balance = Layouts.tranCatBalance(record);
            byKey.put(key(balance.key()), balance);
            fillers.put(key(balance.key()), Layouts.tranCatBalanceFiller(record));
        }
    }

    @Override
    public Optional<TranCatBalance> find(TranCatBalanceKey key) {
        return Optional.ofNullable(byKey.get(key(key)));
    }

    @Override
    public void create(TranCatBalance record) {
        byKey.put(key(record.key()), record);
    }

    @Override
    public void update(TranCatBalance record) {
        byKey.put(key(record.key()), record);
    }

    /**
     * The file in key sequence, as a KSDS is read back. The trailing FILLER of records that were
     * already on the file is preserved, as a COBOL {@code READ INTO} / {@code REWRITE} does.
     */
    public List<String> records() {
        return byKey.entrySet().stream()
                .map(entry -> Layouts.tranCatBalance(entry.getValue(), fillers.get(entry.getKey())))
                .toList();
    }

    /** FD-TRAN-CAT-KEY as the 17 bytes CBTRN02C builds and displays. */
    public static String key(TranCatBalanceKey key) {
        return Cobol.putDigits(key.acctId(), 11) + Cobol.putText(key.typeCd(), 2) + Cobol.putDigits(key.catCd(), 4);
    }
}
