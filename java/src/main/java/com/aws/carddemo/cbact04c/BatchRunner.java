package com.aws.carddemo.cbact04c;

import com.aws.carddemo.cbact04c.io.AccountCodec;
import com.aws.carddemo.cbact04c.io.CardDemoDataLoader;
import com.aws.carddemo.cbact04c.io.FileTransactionWriter;
import com.aws.carddemo.cbact04c.model.AccountRecord;
import com.aws.carddemo.cbact04c.model.TranCatBalRecord;
import com.aws.carddemo.cbact04c.repository.InMemoryAccountRepository;
import com.aws.carddemo.cbact04c.repository.InMemoryCardXrefRepository;
import com.aws.carddemo.cbact04c.repository.InMemoryDisclosureGroupRepository;
import com.aws.carddemo.cbact04c.service.InterestCalculationResult;
import com.aws.carddemo.cbact04c.service.InterestCalculatorService;
import com.aws.carddemo.cbact04c.service.TimestampProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Thin command-line entry point wiring the file readers/writers to
 * {@link InterestCalculatorService}. Mirrors the JCL step
 * {@code EXEC PGM=CBACT04C,PARM='...'} in INTCALC.jcl.
 */
@Component
public class BatchRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BatchRunner.class);

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!args.containsOption("tcatbal")) {
            log.info("No --tcatbal option provided; nothing to do. "
                    + "Usage: --tcatbal=<f> --account=<f> --xref=<f> --discgrp=<f> "
                    + "--output=<f> [--account-out=<f>] [--parm-date=YYYYMMDDHH]");
            return;
        }

        Path tcatbal = Path.of(required(args, "tcatbal"));
        Path account = Path.of(required(args, "account"));
        Path xref = Path.of(required(args, "xref"));
        Path discgrp = Path.of(required(args, "discgrp"));
        Path output = Path.of(required(args, "output"));
        String parmDate = optional(args, "parm-date", "0000000000");

        List<TranCatBalRecord> balances = CardDemoDataLoader.loadTranCatBal(tcatbal);
        InMemoryAccountRepository accounts = CardDemoDataLoader.loadAccounts(account);
        InMemoryCardXrefRepository xrefs = CardDemoDataLoader.loadCardXref(xref);
        InMemoryDisclosureGroupRepository discgrps = CardDemoDataLoader.loadDisclosureGroups(discgrp);

        InterestCalculationResult result;
        try (FileTransactionWriter writer = new FileTransactionWriter(output)) {
            InterestCalculatorService service = new InterestCalculatorService(
                    accounts, xrefs, discgrps, writer, new TimestampProvider.SystemClock());
            log.info("START OF EXECUTION OF PROGRAM CBACT04C");
            result = service.run(balances, parmDate);
        }

        if (args.containsOption("account-out")) {
            writeAccounts(Path.of(required(args, "account-out")), accounts);
        }

        log.info("Processed {} category-balance records; wrote {} interest transactions.",
                result.getRecordCount(), result.getTransactionsWritten());
        log.info("END OF EXECUTION OF PROGRAM CBACT04C");
    }

    private void writeAccounts(Path path, InMemoryAccountRepository accounts) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (AccountRecord account : accounts.asMap().values()) {
                writer.write(AccountCodec.format(account));
                writer.newLine();
            }
        }
    }

    private static String required(ApplicationArguments args, String name) {
        List<String> values = args.getOptionValues(name);
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Missing required option --" + name);
        }
        return values.get(0);
    }

    private static String optional(ApplicationArguments args, String name, String defaultValue) {
        List<String> values = args.getOptionValues(name);
        return (values == null || values.isEmpty()) ? defaultValue : values.get(0);
    }
}
