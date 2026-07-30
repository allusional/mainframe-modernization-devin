package com.carddemo.posttran.files;

import com.carddemo.posttran.Db2Timestamp;
import com.carddemo.posttran.PostTransactionBatch;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the ported batch against the flat sample datasets in {@code app/data/ASCII}, the same
 * inputs the COBOL program is fed by {@code scripts/posttran-parity/run-parity.sh}, and writes
 * the resulting files as text so both runs can be compared record by record.
 *
 * <pre>
 * java -cp target/classes com.carddemo.posttran.files.PostTranBatchRunner &lt;data-dir&gt; &lt;out-dir&gt;
 * </pre>
 */
public final class PostTranBatchRunner {

    private PostTranBatchRunner() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("usage: PostTranBatchRunner <data-dir> <out-dir>");
            System.exit(12);
        }
        Path dataDir = Path.of(args[0]);
        Path outDir = Path.of(args[1]);
        Files.createDirectories(outDir);

        List<String> daily = read(dataDir.resolve("dailytran.txt"), Layouts.DALYTRAN_LENGTH);
        List<String> xrefs = read(dataDir.resolve("cardxref.txt"), Layouts.XREF_LENGTH);
        List<String> accounts = read(dataDir.resolve("acctdata.txt"), Layouts.ACCOUNT_LENGTH);
        List<String> balances = read(dataDir.resolve("tcatbal.txt"), Layouts.TRAN_CAT_BAL_LENGTH);
        checkCodecRoundTrip(daily);

        FlatFileAccountRepository accountRepository = new FlatFileAccountRepository(accounts);
        FlatFileTranCatBalanceRepository balanceRepository = new FlatFileTranCatBalanceRepository(balances);
        FlatFileTransactionWriter transactionWriter = new FlatFileTransactionWriter();
        FlatFileRejectWriter rejectWriter = new FlatFileRejectWriter();

        PostTransactionBatch batch = new PostTransactionBatch(
                new FlatFileDailyTransactionReader(daily),
                new FlatFileXrefRepository(xrefs),
                accountRepository,
                balanceRepository,
                transactionWriter,
                rejectWriter,
                new Db2Timestamp(Clock.systemDefaultZone()),
                System.out::println);

        System.out.println("START OF EXECUTION OF PROGRAM CBTRN02C");
        int returnCode = batch.run();
        System.out.printf("TRANSACTIONS PROCESSED :%09d%n", batch.getTransactionCount());
        System.out.printf("TRANSACTIONS REJECTED  :%09d%n", batch.getRejectCount());
        System.out.println("END OF EXECUTION OF PROGRAM CBTRN02C");

        write(outDir.resolve("tranfile.txt"), transactionWriter.records());
        write(outDir.resolve("acctfile.txt"), accountRepository.records());
        write(outDir.resolve("tcatbal.txt"), balanceRepository.records());
        write(outDir.resolve("rejects.txt"), rejectWriter.records());

        System.exit(returnCode);
    }

    /**
     * The flat files carry the record layouts byte for byte, so decoding and re-encoding an input
     * record has to reproduce it exactly; anything else would make the comparison meaningless.
     */
    private static void checkCodecRoundTrip(List<String> daily) {
        for (int i = 0; i < daily.size(); i++) {
            String encoded = Layouts.dailyTransaction(Layouts.dailyTransaction(daily.get(i)));
            if (!encoded.equals(daily.get(i))) {
                throw new IllegalStateException("DALYTRAN record " + (i + 1) + " does not survive a codec round trip:"
                        + System.lineSeparator() + "in : [" + daily.get(i) + "]"
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
        List<String> lines = records.stream().map(PostTranBatchRunner::rtrim).toList();
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
