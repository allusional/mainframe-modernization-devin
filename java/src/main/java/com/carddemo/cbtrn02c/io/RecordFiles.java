package com.carddemo.cbtrn02c.io;

import com.carddemo.cbtrn02c.model.AccountRecord;
import com.carddemo.cbtrn02c.model.CardXrefRecord;
import com.carddemo.cbtrn02c.model.DailyTransactionRecord;
import com.carddemo.cbtrn02c.model.TranCatBalRecord;
import com.carddemo.cbtrn02c.model.TransactionRecord;
import com.carddemo.cbtrn02c.repository.AccountRepository;
import com.carddemo.cbtrn02c.repository.CardXrefRepository;
import com.carddemo.cbtrn02c.repository.TranCatBalRepository;
import com.carddemo.cbtrn02c.service.BatchSummary;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the CardDemo fixed-width ASCII datasets and writes the batch output files.
 * Blank lines are skipped so trailing newlines in the sample data do not create
 * spurious records.
 */
public final class RecordFiles {

    private RecordFiles() {
    }

    private static List<String> readNonBlankLines(Path path) {
        try {
            List<String> lines = new ArrayList<>();
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
            return lines;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read " + path, e);
        }
    }

    public static List<DailyTransactionRecord> readDailyTransactions(Path path) {
        List<DailyTransactionRecord> records = new ArrayList<>();
        for (String line : readNonBlankLines(path)) {
            records.add(DailyTransactionRecord.parse(line));
        }
        return records;
    }

    public static CardXrefRepository loadCardXref(Path path) {
        CardXrefRepository repo = new CardXrefRepository();
        for (String line : readNonBlankLines(path)) {
            repo.put(CardXrefRecord.parse(line));
        }
        return repo;
    }

    public static AccountRepository loadAccounts(Path path) {
        AccountRepository repo = new AccountRepository();
        for (String line : readNonBlankLines(path)) {
            repo.put(AccountRecord.parse(line));
        }
        return repo;
    }

    public static TranCatBalRepository loadTranCatBalances(Path path) {
        TranCatBalRepository repo = new TranCatBalRepository();
        for (String line : readNonBlankLines(path)) {
            repo.put(TranCatBalRecord.parse(line));
        }
        return repo;
    }

    public static void writeSummary(BatchSummary summary, Path transactionFile, Path rejectFile) {
        List<String> tranLines = new ArrayList<>();
        for (TransactionRecord tran : summary.getPostedTransactions()) {
            tranLines.add(tran.toRecord());
        }
        writeLines(transactionFile, tranLines);
        writeLines(rejectFile, summary.getRejectRecords());
    }

    public static void writeLines(Path path, List<String> lines) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                for (String line : lines) {
                    writer.write(line);
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to write " + path, e);
        }
    }
}
