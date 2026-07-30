package com.carddemo.cbtrn02c;

import com.carddemo.cbtrn02c.copybook.AccountRecord;
import com.carddemo.cbtrn02c.copybook.CardXrefRecord;
import com.carddemo.cbtrn02c.copybook.DalyTranRecord;
import com.carddemo.cbtrn02c.copybook.RejectRecord;
import com.carddemo.cbtrn02c.copybook.TranCatBalRecord;
import com.carddemo.cbtrn02c.copybook.TranRecord;
import com.carddemo.cbtrn02c.io.FixedWidthFile;

import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Command line entry point, the equivalent of the POSTTRAN job step: it takes the place of the JCL
 * DD statements DALYTRAN / XREFFILE / ACCTFILE / TCATBALF (input) and TRANFILE / DALYREJS (output).
 *
 * <pre>
 * java -jar cbtrn02c.jar \
 *      --dalytran=app/data/ASCII/dailytran.txt \
 *      --xref=app/data/ASCII/cardxref.txt \
 *      --acct=app/data/ASCII/acctdata.txt \
 *      --tcatbal=app/data/ASCII/tcatbal.txt \
 *      --out-dir=target/java-output \
 *      --current-date=2022071112000000
 * </pre>
 *
 * {@code --current-date} pins TRAN-PROC-TS (format {@code YYYYMMDDHHMMSS[hh]}, the same value as
 * GnuCOBOL's {@code COB_CURRENT_DATE}) so that a run is reproducible; without it the wall clock is
 * used, like FUNCTION CURRENT-DATE in the COBOL program. The process exits with the COBOL
 * RETURN-CODE: 4 when at least one transaction was rejected, 0 otherwise.
 */
public final class Cbtrn02c {

    private Cbtrn02c() {
    }

    public static void main(String[] args) {
        Map<String, String> options = parseOptions(args);
        Path dalytran = requiredPath(options, "dalytran");
        Path xref = requiredPath(options, "xref");
        Path acct = requiredPath(options, "acct");
        Path tcatbal = requiredPath(options, "tcatbal");
        Path outDir = Path.of(options.getOrDefault("out-dir", "target/java-output"));
        String currentDate = options.get("current-date");

        Supplier<String> processingTimestamp = currentDate == null
                ? Db2Timestamp.fromClock(Clock.systemDefaultZone())
                : Db2Timestamp.fixed(Db2Timestamp.fromCobCurrentDate(currentDate));

        Map<String, CardXrefRecord> xrefFile = new LinkedHashMap<>();
        for (String raw : FixedWidthFile.read(xref, CardXrefRecord.LENGTH)) {
            CardXrefRecord record = CardXrefRecord.parse(raw);
            xrefFile.put(record.cardNumber(), record);
        }
        NavigableMap<String, AccountRecord> accountFile = new TreeMap<>();
        for (String raw : FixedWidthFile.read(acct, AccountRecord.LENGTH)) {
            AccountRecord record = AccountRecord.parse(raw);
            accountFile.put(record.id(), record);
        }
        NavigableMap<String, TranCatBalRecord> tranCatBalFile = new TreeMap<>();
        for (String raw : FixedWidthFile.read(tcatbal, TranCatBalRecord.LENGTH)) {
            TranCatBalRecord record = TranCatBalRecord.parse(raw);
            tranCatBalFile.put(record.key(), record);
        }
        List<DalyTranRecord> dalyTranFile = FixedWidthFile.read(dalytran, DalyTranRecord.LENGTH).stream()
                .map(DalyTranRecord::parse)
                .toList();

        PostTranBatch batch = new PostTranBatch(xrefFile, accountFile, tranCatBalFile, processingTimestamp,
                System.out::println);
        PostTranBatch.Result result = batch.run(dalyTranFile);

        FixedWidthFile.write(outDir.resolve("transact.dat"),
                batch.transactFile().stream().map(TranRecord::serialize).collect(Collectors.toList()),
                TranRecord.LENGTH);
        FixedWidthFile.write(outDir.resolve("acctdata.dat"),
                batch.accountFile().stream().map(AccountRecord::serialize).collect(Collectors.toList()),
                AccountRecord.LENGTH);
        FixedWidthFile.write(outDir.resolve("tcatbal.dat"),
                batch.tranCatBalFile().stream().map(TranCatBalRecord::serialize).collect(Collectors.toList()),
                TranCatBalRecord.LENGTH);
        FixedWidthFile.write(outDir.resolve("dalyrejs.dat"),
                batch.rejectFile().stream().map(RejectRecord::serialize).collect(Collectors.toList()),
                RejectRecord.LENGTH);

        System.exit(result.returnCode());
    }

    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--") || !arg.contains("=")) {
                throw new IllegalArgumentException("expected --name=value, got: " + arg);
            }
            int eq = arg.indexOf('=');
            options.put(arg.substring(2, eq), arg.substring(eq + 1));
        }
        return options;
    }

    private static Path requiredPath(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null) {
            throw new IllegalArgumentException("missing required option --" + name);
        }
        return Path.of(value);
    }
}
