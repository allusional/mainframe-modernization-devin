package com.carddemo.posting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whole program over the 300 shipped records in {@code app/data/ASCII}, through the same
 * command line the operator would use. The counts asserted here are the ones the Phase 4
 * harness compares against GnuCOBOL running the unmodified COBOL.
 */
class SampleDataEndToEndTest {

    private static final Path DATA = repositoryRoot().resolve("app/data/ASCII");
    private static final Clock FIXED = Clock.fixed(Instant.parse("2024-06-02T01:02:03.45Z"), ZoneOffset.UTC);

    @TempDir
    Path workspace;

    @Test
    @DisplayName("bug-for-bug: 300 read, 38 rejected (all 0102), 262 posted, RC=4")
    void reproducesTheCobolNumbers() throws IOException {
        Run run = execute("--bug-for-bug");

        assertEquals(4, run.returnCode());
        assertEquals(300, run.dailyFeedSize());
        assertEquals(262, run.postedRecords().size());
        assertEquals(38, run.rejectRecords().size());
        assertEquals(Map.of("0102", 38L), run.rejectsByReason());
        assertTrue(run.output().contains("TRANSACTIONS PROCESSED :000000300"));
        assertTrue(run.output().contains("TRANSACTIONS REJECTED  :000000038"));
        assertTrue(run.output().startsWith("START OF EXECUTION OF PROGRAM CBTRN02C"));
        assertTrue(run.output().contains("END OF EXECUTION OF PROGRAM CBTRN02C"));
    }

    @Test
    @DisplayName("corrected: 15 of those 38 rejects were refunds counted as spend (D4)")
    void correctedBehaviourApprovesFifteenMore() throws IOException {
        Run run = execute();

        assertEquals(4, run.returnCode());     // still RC=4: 23 genuine over-limit rejects remain
        assertEquals(277, run.postedRecords().size());
        assertEquals(23, run.rejectRecords().size());
        assertEquals(Map.of("0102", 23L), run.rejectsByReason());
    }

    @Test
    @DisplayName("every output record is the length its DD statement says it is")
    void outputRecordLengths() throws IOException {
        Run run = execute("--bug-for-bug");

        run.postedRecords().forEach(record -> assertEquals(350, record.length()));
        run.rejectRecords().forEach(record -> assertEquals(430, record.length()));
        run.accountRecords().forEach(record -> assertEquals(300, record.length()));
        run.categoryBalanceRecords().forEach(record -> assertEquals(50, record.length()));
    }

    @Test
    @DisplayName("the run leaves the account master with the same 50 accounts, in key order")
    void accountMasterKeepsItsRecords() throws IOException {
        Run run = execute("--bug-for-bug");

        List<String> keys = run.accountRecords().stream().map(record -> record.substring(0, 11)).toList();
        assertEquals(50, keys.size());
        assertEquals(keys.stream().sorted().toList(), keys);
    }

    @Test
    @DisplayName("posting creates the 50 type-03 category buckets the sample data does not have")
    void categoryBalancesGrow() throws IOException {
        long before = Files.readAllLines(DATA.resolve("tcatbal.txt")).size();

        Run run = execute("--bug-for-bug");

        assertEquals(before + 50, run.categoryBalanceRecords().size());
    }

    @Test
    @DisplayName("the processed timestamp is stamped in DB2 format on every posted record")
    void processedTimestampFormat() throws IOException {
        Run run = execute("--bug-for-bug");

        run.postedRecords().forEach(record ->
                assertTrue(record.substring(304, 330).matches("\\d{4}-\\d{2}-\\d{2}-\\d{2}\\.\\d{2}\\.\\d{2}\\.\\d{6}"),
                        "TRAN-PROC-TS was: " + record.substring(304, 330)));
    }

    private Run execute(String... flags) throws IOException {
        Path accounts = workspace.resolve("acctdata.txt");
        Path balances = workspace.resolve("tcatbal.txt");
        Path posted = workspace.resolve("transact.txt");
        Path rejects = workspace.resolve("dalyrejs.txt");
        Files.copy(DATA.resolve("acctdata.txt"), accounts, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(DATA.resolve("tcatbal.txt"), balances, StandardCopyOption.REPLACE_EXISTING);

        String[] arguments = java.util.stream.Stream.concat(
                java.util.stream.Stream.of(
                        "--dalytran", DATA.resolve("dailytran.txt").toString(),
                        "--xreffile", DATA.resolve("cardxref.txt").toString(),
                        "--acctfile", accounts.toString(),
                        "--tcatbalf", balances.toString(),
                        "--tranfile", posted.toString(),
                        "--dalyrejs", rejects.toString()),
                java.util.stream.Stream.of(flags)).toArray(String[]::new);

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        int returnCode;
        try (PrintStream out = new PrintStream(captured, true, StandardCharsets.ISO_8859_1)) {
            returnCode = Cbtrn02c.run(arguments, out, out, FIXED);
        }
        return new Run(returnCode, captured.toString(StandardCharsets.ISO_8859_1),
                Files.readAllLines(DATA.resolve("dailytran.txt"), StandardCharsets.ISO_8859_1).size(),
                Files.readAllLines(posted, StandardCharsets.ISO_8859_1),
                Files.readAllLines(rejects, StandardCharsets.ISO_8859_1),
                Files.readAllLines(accounts, StandardCharsets.ISO_8859_1),
                Files.readAllLines(balances, StandardCharsets.ISO_8859_1));
    }

    private record Run(int returnCode, String output, int dailyFeedSize, List<String> postedRecords,
                       List<String> rejectRecords, List<String> accountRecords,
                       List<String> categoryBalanceRecords) {

        /** The reject reason is the four digits at offset 350 of each 430 byte record. */
        Map<String, Long> rejectsByReason() {
            return new TreeMap<>(rejectRecords.stream()
                    .collect(Collectors.groupingBy(record -> record.substring(350, 354), Collectors.counting())));
        }
    }

    private static Path repositoryRoot() {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null && !Files.isDirectory(directory.resolve("app/data/ASCII"))) {
            directory = directory.getParent();
        }
        if (directory == null) {
            throw new IllegalStateException("Could not find app/data/ASCII above " + Path.of("").toAbsolutePath());
        }
        return directory;
    }
}
