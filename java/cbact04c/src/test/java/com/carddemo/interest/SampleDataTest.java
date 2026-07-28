package com.carddemo.interest;

import com.carddemo.interest.io.FixedWidthFiles;
import com.carddemo.interest.records.AccountRecord;
import com.carddemo.interest.records.TransactionRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End to end run of the ported job against the sample datasets that ship with CardDemo
 * (app/data/ASCII), driven through the command line entry point.
 */
class SampleDataTest {

    private static final Path DATA = Path.of("..", "..", "app", "data", "ASCII").normalize();

    @Test
    void runsTheShippedSampleDataEndToEnd(@TempDir Path work) throws IOException {
        Path accountCopy = work.resolve("acctdata.txt");
        Files.copy(DATA.resolve("acctdata.txt"), accountCopy);
        Path transactions = work.resolve("systran.txt");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exitCode = Cbact04c.run(new String[]{
                "--parm", "2022071800",
                "--tcatbal", DATA.resolve("tcatbal.txt").toString(),
                "--acct", accountCopy.toString(),
                "--xref", DATA.resolve("cardxref.txt").toString(),
                "--discgrp", DATA.resolve("discgrp.txt").toString(),
                "--out-transact", transactions.toString()
        }, new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));

        assertEquals(0, exitCode, err.toString(StandardCharsets.UTF_8));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("END OF EXECUTION OF PROGRAM CBACT04C"));

        List<String> written = FixedWidthFiles.readRecords(transactions);
        assertEquals(50, written.size(), "one interest transaction per category balance row");
        for (String record : written) {
            assertEquals(TransactionRecord.LENGTH, record.length());
        }
        TransactionRecord first = TransactionRecord.parse(written.get(0));
        assertEquals("2022071800000001", first.transactionId());
        assertEquals("01", first.typeCode());
        assertEquals("0005", first.categoryCode());
        assertEquals("System", first.source().trim());
        assertEquals("Int. for a/c 00000000001", first.description().trim());

        List<String> updated = FixedWidthFiles.readRecords(accountCopy);
        assertEquals(50, updated.size());
        for (String record : updated) {
            assertEquals(AccountRecord.LENGTH, record.length());
        }
        for (String record : updated) {
            AccountRecord account = AccountRecord.parse(record);
            assertEquals(0, account.currentCycleCredit().signum(), "cycle credit is reset by the job");
            assertEquals(0, account.currentCycleDebit().signum(), "cycle debit is reset by the job");
        }
    }

    /**
     * Every account in the shipped sample data has a blank ACCT-GROUP-ID (the value that looks
     * like a group id, A000000000, sits in ACCT-ADDR-ZIP), so every rate lookup misses and the
     * DEFAULT disclosure group is what actually gets used.
     */
    @Test
    void everySampleAccountFallsBackToTheDefaultDisclosureGroup() throws IOException {
        for (String record : FixedWidthFiles.readRecords(DATA.resolve("acctdata.txt"))) {
            assertTrue(AccountRecord.parse(record).groupId().isBlank(),
                    "sample account master is expected to carry a blank group id");
        }
        boolean hasDefaultGroup = FixedWidthFiles.readRecords(DATA.resolve("discgrp.txt")).stream()
                .anyMatch(record -> record.startsWith("DEFAULT"));
        assertTrue(hasDefaultGroup, "sample rate card is expected to contain a DEFAULT group");
    }

    /** Parsing then re-rendering the sample account master must be byte for byte identical. */
    @Test
    void accountRecordsRoundTripWithoutLoss() throws IOException {
        for (String record : FixedWidthFiles.readRecords(DATA.resolve("acctdata.txt"))) {
            assertEquals(record, AccountRecord.parse(record).toRecord());
        }
    }
}
