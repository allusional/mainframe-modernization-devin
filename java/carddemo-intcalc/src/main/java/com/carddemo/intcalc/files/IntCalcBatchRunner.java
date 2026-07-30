package com.carddemo.intcalc.files;

import com.carddemo.intcalc.AbendException;
import com.carddemo.intcalc.Db2Timestamp;
import com.carddemo.intcalc.InterestCalculationBatch;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Runs the ported batch against the flat sample datasets in {@code app/data/ASCII}, the same
 * inputs the COBOL program is fed by {@code scripts/intcalc-parity/run-parity.sh}, and writes the
 * files it produces as text so both runs can be compared record by record.
 *
 * <pre>
 * java -cp target/classes com.carddemo.intcalc.files.IntCalcBatchRunner &lt;data-dir&gt; &lt;out-dir&gt; [parm-date]
 * </pre>
 *
 * <p>The parm date defaults to {@code 2022071800}, the PARM of {@code STEP15} in
 * {@code app/jcl/INTCALC.jcl}; it becomes the first ten bytes of every TRAN-ID.
 */
public final class IntCalcBatchRunner {

    private static final String DEFAULT_PARM_DATE = "2022071800";

    private IntCalcBatchRunner() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2 || args.length > 3) {
            System.err.println("usage: IntCalcBatchRunner <data-dir> <out-dir> [parm-date]");
            System.exit(12);
        }
        Path dataDir = Path.of(args[0]);
        Path outDir = Path.of(args[1]);
        String parmDate = args.length == 3 ? args[2] : DEFAULT_PARM_DATE;
        Files.createDirectories(outDir);

        List<String> balances = read(dataDir.resolve("tcatbal.txt"), Layouts.TRAN_CAT_BAL_LENGTH);
        List<String> xrefs = read(dataDir.resolve("cardxref.txt"), Layouts.XREF_LENGTH);
        List<String> groups = read(dataDir.resolve("discgrp.txt"), Layouts.DISC_GROUP_LENGTH);
        List<String> accounts = read(dataDir.resolve("acctdata.txt"), Layouts.ACCOUNT_LENGTH);
        checkCodecRoundTrip("TCATBALF", balances, record -> Layouts.tranCatBalance(Layouts.tranCatBalance(record)));
        checkCodecRoundTrip("XREFFILE", xrefs, record -> Layouts.xref(Layouts.xref(record)));
        checkCodecRoundTrip("DISCGRP", groups,
                record -> Layouts.discGroup(Layouts.discGroup(record), Layouts.discGroupFiller(record)));
        checkCodecRoundTrip("ACCTFILE", accounts,
                record -> Layouts.account(Layouts.account(record), Layouts.accountFiller(record)));

        FlatFileAccountRepository accountRepository = new FlatFileAccountRepository(accounts);
        FlatFileTransactionWriter transactionWriter = new FlatFileTransactionWriter();

        InterestCalculationBatch batch = new InterestCalculationBatch(
                new FlatFileTranCatBalanceReader(balances),
                new FlatFileXrefRepository(xrefs),
                new FlatFileDiscGroupRepository(groups),
                accountRepository,
                transactionWriter,
                new Db2Timestamp(Clock.systemDefaultZone()),
                System.out::println,
                parmDate);

        System.out.println("START OF EXECUTION OF PROGRAM CBACT04C");
        int returnCode;
        try {
            returnCode = batch.run();
        } catch (AbendException abend) {
            write(outDir.resolve("tranfile.txt"), transactionWriter.records());
            write(outDir.resolve("acctfile.txt"), accountRepository.records());
            System.out.flush();
            System.exit(abend.getAbendCode() % 256);
            return;
        }
        System.out.println("END OF EXECUTION OF PROGRAM CBACT04C");

        write(outDir.resolve("tranfile.txt"), transactionWriter.records());
        write(outDir.resolve("acctfile.txt"), accountRepository.records());
        Files.writeString(outDir.resolve("counters.txt"),
                "TCATBALF-RECORDS-READ    :" + batch.getRecordCount() + System.lineSeparator()
                        + "TRANFILE-RECORDS-WRITTEN :" + transactionWriter.records().size() + System.lineSeparator()
                        + "ACCTFILE-REWRITES        :" + accountRepository.rewriteCount() + System.lineSeparator(),
                StandardCharsets.ISO_8859_1);

        System.exit(returnCode);
    }

    /**
     * The flat files carry the record layouts byte for byte, so decoding and re-encoding an input
     * record has to reproduce it exactly; anything else would make the comparison meaningless.
     */
    private static void checkCodecRoundTrip(String file, List<String> records, Function<String, String> codec) {
        for (int i = 0; i < records.size(); i++) {
            String encoded = codec.apply(records.get(i));
            if (!encoded.equals(records.get(i))) {
                throw new IllegalStateException(file + " record " + (i + 1)
                        + " does not survive a codec round trip:"
                        + System.lineSeparator() + "in : [" + records.get(i) + "]"
                        + System.lineSeparator() + "out: [" + encoded + "]");
            }
        }
    }

    private static List<String> read(Path file, int recordLength) throws IOException {
        List<String> records = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.ISO_8859_1)) {
            String record = line.replace("\r", "");
            if (record.isEmpty()) {
                continue;
            }
            records.add(record.length() >= recordLength
                    ? record.substring(0, recordLength)
                    : record + " ".repeat(recordLength - record.length()));
        }
        return records;
    }

    /** Writes records the way GnuCOBOL writes a LINE SEQUENTIAL file: trailing spaces removed. */
    private static void write(Path file, List<String> records) throws IOException {
        List<String> lines = records.stream().map(IntCalcBatchRunner::rtrim).toList();
        Files.write(file, lines, StandardCharsets.ISO_8859_1);
    }

    private static String rtrim(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == ' ') {
            end--;
        }
        return value.substring(0, end);
    }
}
