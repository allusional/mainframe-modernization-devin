package com.carddemo.cbtrn02c;

import com.carddemo.cbtrn02c.copybook.AccountRecord;
import com.carddemo.cbtrn02c.copybook.CardXrefRecord;
import com.carddemo.cbtrn02c.copybook.DalytranRecord;
import com.carddemo.cbtrn02c.copybook.TranCatBalRecord;
import com.carddemo.cbtrn02c.io.RecordFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Materialises a parity scenario: turns the newline delimited datasets under
 * {@code parity/data} (or {@code app/data/ASCII} for the "full" scenario) into
 * fixed length record files and runs the Java port against them.
 *
 * <p>Mirrors {@code parity/scripts/prep_inputs.sh}.
 */
final class ScenarioFixture {

    /** Module root, i.e. the directory surefire runs in. */
    static final Path MODULE = Path.of("").toAbsolutePath();
    static final Path REPO = MODULE.getParent().getParent();

    final Path directory;
    final Path transact;
    final Path acctdata;
    final Path tcatbal;
    final Path dalyrejs;
    private final Cbtrn02c.Datasets datasets;

    private ScenarioFixture(Path directory, Cbtrn02c.Datasets datasets) {
        this.directory = directory;
        this.datasets = datasets;
        this.transact = directory.resolve("TRANSACT.dat");
        this.acctdata = directory.resolve("ACCTDATA.dat");
        this.tcatbal = directory.resolve("TCATBAL.dat");
        this.dalyrejs = directory.resolve("DALYREJS.dat");
    }

    static Path sourceDirectory(String scenario) {
        return scenario.equals("full")
                ? REPO.resolve("app/data/ASCII")
                : MODULE.resolve("parity/data").resolve(scenario);
    }

    static Path goldenDirectory(String scenario) {
        return MODULE.resolve("parity/golden").resolve(scenario);
    }

    static ScenarioFixture prepare(String scenario, Path workDirectory) throws IOException {
        Path source = sourceDirectory(scenario);
        Files.createDirectories(workDirectory);
        pad(source.resolve("dailytran.txt"), DalytranRecord.LENGTH, workDirectory.resolve("DALYTRAN.dat"));
        pad(source.resolve("cardxref.txt"), CardXrefRecord.LENGTH, workDirectory.resolve("CARDXREF.dat"));
        pad(source.resolve("acctdata.txt"), AccountRecord.LENGTH, workDirectory.resolve("ACCTDATA.dat"));
        pad(source.resolve("tcatbal.txt"), TranCatBalRecord.LENGTH, workDirectory.resolve("TCATBAL.dat"));
        Cbtrn02c.Datasets datasets = new Cbtrn02c.Datasets(
                workDirectory.resolve("DALYTRAN.dat"),
                workDirectory.resolve("TRANSACT.dat"),
                workDirectory.resolve("CARDXREF.dat"),
                workDirectory.resolve("DALYREJS.dat"),
                workDirectory.resolve("ACCTDATA.dat"),
                workDirectory.resolve("TCATBAL.dat"));
        return new ScenarioFixture(workDirectory, datasets);
    }

    Cbtrn02c.Result run() throws IOException {
        return new Cbtrn02c(java.time.Clock.systemDefaultZone(), line -> { }).run(datasets);
    }

    List<String> rejects() throws IOException {
        return RecordFile.read(dalyrejs, 430);
    }

    List<String> transactions() throws IOException {
        return RecordFile.read(transact, 350);
    }

    List<AccountRecord> accounts() throws IOException {
        List<AccountRecord> accounts = new ArrayList<>();
        for (String record : RecordFile.read(acctdata, AccountRecord.LENGTH)) {
            accounts.add(AccountRecord.parse(record));
        }
        return accounts;
    }

    List<TranCatBalRecord> categoryBalances() throws IOException {
        List<TranCatBalRecord> balances = new ArrayList<>();
        for (String record : RecordFile.read(tcatbal, TranCatBalRecord.LENGTH)) {
            balances.add(TranCatBalRecord.parse(record));
        }
        return balances;
    }

    private static void pad(Path source, int length, Path target) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String line : Files.readAllLines(source, StandardCharsets.ISO_8859_1)) {
            String record = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
            if (record.length() > length) {
                throw new IOException(source + " has a record of " + record.length() + " bytes, expected " + length);
            }
            sb.append(record).append(" ".repeat(length - record.length()));
        }
        Files.write(target, sb.toString().getBytes(StandardCharsets.ISO_8859_1));
    }
}
