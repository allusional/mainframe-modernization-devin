package com.carddemo.cbtrn02c;

import com.carddemo.cbtrn02c.copybook.AccountRecord;
import com.carddemo.cbtrn02c.copybook.RejectRecord;
import com.carddemo.cbtrn02c.copybook.TranCatBalRecord;
import com.carddemo.cbtrn02c.copybook.TranRecord;
import com.carddemo.cbtrn02c.io.FixedWidthFile;
import com.carddemo.cbtrn02c.parity.Layouts;
import com.carddemo.cbtrn02c.parity.ParityChecker;
import com.carddemo.cbtrn02c.testsupport.BatchFixture;
import com.carddemo.cbtrn02c.testsupport.Fixtures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Business logic parity: runs the port over the ASCII sample fixtures with the processing timestamp
 * pinned to the same value as the GnuCOBOL run, then compares every output record field by field
 * with the golden files captured from the COBOL program
 * (see {@code cobol-baseline/run_baseline.sh}).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CobolParityTest {

    private static final Path JAVA_OUTPUT = Path.of("target/java-output");

    private List<String> displayed = List.of();
    private PostTranBatch.Result result;

    @BeforeAll
    void runTheJavaPort() {
        assertTrue(Fixtures.cobolBaselineExists(),
                "COBOL baseline missing - run java/cbtrn02c/cobol-baseline/run_baseline.sh");

        BatchFixture fixture = BatchFixture.fromAsciiFixtures();
        PostTranBatch batch = fixture.batch();
        result = batch.run(BatchFixture.dailyTransactions());
        displayed = List.copyOf(fixture.displayed());

        FixedWidthFile.write(JAVA_OUTPUT.resolve("transact.dat"),
                batch.transactFile().stream().map(TranRecord::serialize).collect(Collectors.toList()),
                TranRecord.LENGTH);
        FixedWidthFile.write(JAVA_OUTPUT.resolve("acctdata.dat"),
                batch.accountFile().stream().map(AccountRecord::serialize).collect(Collectors.toList()),
                AccountRecord.LENGTH);
        FixedWidthFile.write(JAVA_OUTPUT.resolve("tcatbal.dat"),
                batch.tranCatBalFile().stream().map(TranCatBalRecord::serialize).collect(Collectors.toList()),
                TranCatBalRecord.LENGTH);
        FixedWidthFile.write(JAVA_OUTPUT.resolve("dalyrejs.dat"),
                batch.rejectFile().stream().map(RejectRecord::serialize).collect(Collectors.toList()),
                RejectRecord.LENGTH);
    }

    static List<Layouts.RecordLayout> layouts() {
        return Layouts.ALL;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("layouts")
    void everyOutputRecordMatchesTheCobolBaseline(Layouts.RecordLayout layout) {
        ParityChecker.FileComparison comparison =
                ParityChecker.compare(layout, Fixtures.cobolBaseline(), JAVA_OUTPUT);

        assertEquals(comparison.expectedRecords(), comparison.actualRecords(),
                () -> layout.fileName() + ": record count differs from the COBOL baseline");
        assertEquals(List.of(), comparison.differences(),
                () -> layout.fileName() + ": " + comparison.differences().size() + " field differences");
        assertTrue(comparison.expectedRecords() > 0, "the baseline must not be empty");
    }

    @Test
    void outputFilesAreByteForByteIdenticalToTheCobolBaseline() throws IOException {
        for (Layouts.RecordLayout layout : Layouts.ALL) {
            byte[] cobol = Files.readAllBytes(Fixtures.cobolBaseline().resolve(layout.fileName()));
            byte[] java = Files.readAllBytes(JAVA_OUTPUT.resolve(layout.fileName()));
            assertEquals(cobol.length, java.length, layout.fileName() + ": file size differs");
            assertTrue(java.length > 0 && java.length == cobol.length && java.length % layout.recordLength() == 0);
            assertArrayEqualsWithFieldContext(layout, cobol, java);
        }
    }

    @Test
    void countersAndReturnCodeMatchTheCobolRun() throws IOException {
        List<String> cobolLog = Files.readAllLines(Fixtures.cobolBaseline().resolve("cobol-run.log"));
        String processed = cobolLog.stream().filter(l -> l.startsWith("TRANSACTIONS PROCESSED")).findFirst()
                .orElseThrow();
        String rejected = cobolLog.stream().filter(l -> l.startsWith("TRANSACTIONS REJECTED")).findFirst()
                .orElseThrow();
        int cobolReturnCode = Integer.parseInt(
                Files.readString(Fixtures.cobolBaseline().resolve("return-code.txt")).trim());

        assertEquals(processed, "TRANSACTIONS PROCESSED :" + String.format("%09d", result.transactionsProcessed()));
        assertEquals(rejected, "TRANSACTIONS REJECTED  :" + String.format("%09d", result.transactionsRejected()));
        assertEquals(cobolReturnCode, result.returnCode());
    }

    @Test
    void displayOutputMatchesTheCobolRun() throws IOException {
        List<String> cobolLog = Files.readAllLines(Fixtures.cobolBaseline().resolve("cobol-run.log")).stream()
                .filter(line -> !line.startsWith("COBOL RETURN-CODE:"))
                .toList();

        assertEquals(cobolLog, displayed);
    }

    private static void assertArrayEqualsWithFieldContext(Layouts.RecordLayout layout, byte[] cobol, byte[] java) {
        for (int i = 0; i < cobol.length; i++) {
            if (cobol[i] != java[i]) {
                int record = i / layout.recordLength();
                int offset = i % layout.recordLength();
                String field = layout.fields().stream()
                        .filter(f -> offset >= f.offset() && offset < f.offset() + f.length())
                        .map(Layouts.Field::name)
                        .findFirst()
                        .orElse("<unmapped>");
                throw new AssertionError(layout.fileName() + ": first byte difference in record " + (record + 1)
                        + ", field " + field + " (offset " + offset + ")");
            }
        }
    }
}
