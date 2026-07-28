package com.carddemo.cbtrn02c;

import com.carddemo.cbtrn02c.io.RecordFiles;
import com.carddemo.cbtrn02c.model.DailyTransactionRecord;
import com.carddemo.cbtrn02c.repository.AccountRepository;
import com.carddemo.cbtrn02c.repository.CardXrefRepository;
import com.carddemo.cbtrn02c.repository.TranCatBalRepository;
import com.carddemo.cbtrn02c.service.BatchSummary;
import com.carddemo.cbtrn02c.service.DailyTransactionPostingJob;
import com.carddemo.cbtrn02c.service.ProcessingTimestampProvider;
import com.carddemo.cbtrn02c.service.TransactionPostingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Thin command-line runner that mirrors the CBTRN02C PROCEDURE DIVISION driver:
 * open the files (load repositories), process the daily transactions, and report
 * the counters. File locations are supplied as arguments; when they are absent the
 * runner simply prints usage so the application context still starts cleanly (e.g.
 * during tests).
 *
 * <pre>
 *   --dalytran=PATH  --xref=PATH  --acct=PATH  --tcatbal=PATH
 *   --tranfile=PATH  --rejects=PATH
 * </pre>
 */
@Component
public class PostingJobRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PostingJobRunner.class);

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("dalytran")) {
            log.info("START OF EXECUTION OF PROGRAM CBTRN02C (no input supplied)");
            log.info("Usage: --dalytran=PATH --xref=PATH --acct=PATH --tcatbal=PATH "
                    + "--tranfile=PATH --rejects=PATH");
            return;
        }

        log.info("START OF EXECUTION OF PROGRAM CBTRN02C");
        List<DailyTransactionRecord> dailyTransactions =
                RecordFiles.readDailyTransactions(Path.of(option(args, "dalytran")));
        CardXrefRepository xref = RecordFiles.loadCardXref(Path.of(option(args, "xref")));
        AccountRepository accounts = RecordFiles.loadAccounts(Path.of(option(args, "acct")));
        TranCatBalRepository tranCatBal = RecordFiles.loadTranCatBalances(Path.of(option(args, "tcatbal")));

        TransactionPostingService service = new TransactionPostingService(
                xref, accounts, tranCatBal, ProcessingTimestampProvider.systemClock());
        BatchSummary summary = new DailyTransactionPostingJob(service).run(dailyTransactions);

        RecordFiles.writeSummary(summary,
                Path.of(option(args, "tranfile", "TRANSACT.OUT")),
                Path.of(option(args, "rejects", "DALYREJS.OUT")));

        log.info("TRANSACTIONS PROCESSED : {}", summary.getTransactionCount());
        log.info("TRANSACTIONS REJECTED  : {}", summary.getRejectCount());
        log.info("RETURN-CODE            : {}", summary.getReturnCode());
        log.info("END OF EXECUTION OF PROGRAM CBTRN02C");
    }

    private static String option(ApplicationArguments args, String name) {
        List<String> values = args.getOptionValues(name);
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Missing required argument --" + name);
        }
        return values.get(0);
    }

    private static String option(ApplicationArguments args, String name, String defaultValue) {
        List<String> values = args.getOptionValues(name);
        return (values == null || values.isEmpty()) ? defaultValue : values.get(0);
    }
}
