package com.carddemo.cbtrn02c.testsupport;

import com.carddemo.cbtrn02c.Db2Timestamp;
import com.carddemo.cbtrn02c.PostTranBatch;
import com.carddemo.cbtrn02c.copybook.AccountRecord;
import com.carddemo.cbtrn02c.copybook.CardXrefRecord;
import com.carddemo.cbtrn02c.copybook.DalyTranRecord;
import com.carddemo.cbtrn02c.copybook.TranCatBalRecord;
import com.carddemo.cbtrn02c.io.FixedWidthFile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/** Builds a {@link PostTranBatch} over the keyed files, either from fixtures or from literals. */
public final class BatchFixture {

    /** The processing timestamp pinned in cobol-baseline/run_baseline.sh (COB_CURRENT_DATE). */
    public static final String PINNED_COB_CURRENT_DATE = "2022071112000000";
    public static final String PINNED_PROC_TS = Db2Timestamp.fromCobCurrentDate(PINNED_COB_CURRENT_DATE);

    private final Map<String, CardXrefRecord> xrefFile = new LinkedHashMap<>();
    private final NavigableMap<String, AccountRecord> accountFile = new TreeMap<>();
    private final NavigableMap<String, TranCatBalRecord> tranCatBalFile = new TreeMap<>();
    private final List<String> displayed = new ArrayList<>();

    public static BatchFixture empty() {
        return new BatchFixture();
    }

    /** Loads the XREF, ACCOUNT and TCATBAL keyed files from the ASCII sample data. */
    public static BatchFixture fromAsciiFixtures() {
        BatchFixture fixture = new BatchFixture();
        fixture.load(Fixtures.cardXref(), Fixtures.acctData(), Fixtures.tcatBal());
        return fixture;
    }

    public BatchFixture withXref(String raw) {
        CardXrefRecord record = CardXrefRecord.parse(raw);
        xrefFile.put(record.cardNumber(), record);
        return this;
    }

    public BatchFixture withAccount(String raw) {
        AccountRecord record = AccountRecord.parse(raw);
        accountFile.put(record.id(), record);
        return this;
    }

    public BatchFixture withTranCatBal(String raw) {
        TranCatBalRecord record = TranCatBalRecord.parse(raw);
        tranCatBalFile.put(record.key(), record);
        return this;
    }

    public PostTranBatch batch() {
        return new PostTranBatch(xrefFile, accountFile, tranCatBalFile,
                Db2Timestamp.fixed(PINNED_PROC_TS), displayed::add);
    }

    public List<String> displayed() {
        return displayed;
    }

    public AccountRecord account(String id) {
        return accountFile.get(id);
    }

    public TranCatBalRecord tranCatBal(String key) {
        return tranCatBalFile.get(key);
    }

    /** The daily transaction file, read from the ASCII fixture. */
    public static List<DalyTranRecord> dailyTransactions() {
        return FixedWidthFile.read(Fixtures.dailyTran(), DalyTranRecord.LENGTH).stream()
                .map(DalyTranRecord::parse)
                .toList();
    }

    private void load(Path xref, Path account, Path tranCatBal) {
        FixedWidthFile.read(xref, CardXrefRecord.LENGTH).forEach(this::withXref);
        FixedWidthFile.read(account, AccountRecord.LENGTH).forEach(this::withAccount);
        FixedWidthFile.read(tranCatBal, TranCatBalRecord.LENGTH).forEach(this::withTranCatBal);
    }
}
