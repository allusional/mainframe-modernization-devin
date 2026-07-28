package com.carddemo.interest;

import com.carddemo.interest.io.FixedWidthFiles;
import com.carddemo.interest.records.AccountRecord;
import com.carddemo.interest.records.CardXrefRecord;
import com.carddemo.interest.records.DisclosureGroupRecord;
import com.carddemo.interest.records.TranCatBalRecord;
import com.carddemo.interest.records.TransactionRecord;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Command line entry point, the equivalent of the INTCALC job step
 * {@code EXEC PGM=CBACT04C,PARM='2022071800'}.
 */
public final class Cbact04c {

    private static final String USAGE = """
            Usage: cbact04c --parm <10 char date> \\
                            --tcatbal <file> --acct <file> --xref <file> --discgrp <file> \\
                            --out-transact <file> [--out-acct <file>] [--emulate-final-account-quirk]

              --parm            the JCL PARM value, e.g. 2022071800 (prefixes generated transaction ids)
              --tcatbal         transaction category balance file  (DD TCATBALF, copybook CVTRA01Y)
              --acct            account master                     (DD ACCTFILE, copybook CVACT01Y)
              --xref            card cross reference               (DD XREFFILE, copybook CVACT03Y)
              --discgrp         disclosure group / rate card       (DD DISCGRP,  copybook CVTRA02Y)
              --out-transact    generated interest transactions    (DD TRANSACT, copybook CVTRA05Y)
              --out-acct        updated account master; defaults to overwriting --acct in place,
                                which is what the COBOL does (it opens ACCTFILE I-O)
              --emulate-final-account-quirk
                                reproduce the COBOL defect where the last account in the balance
                                file has its interest transactions written but its master record
                                left untouched
            """;

    private Cbact04c() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        Map<String, String> options;
        try {
            options = parseArguments(args);
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            err.println();
            err.println(USAGE);
            return 12;
        }

        out.println("START OF EXECUTION OF PROGRAM CBACT04C");
        try {
            Path accountPath = Path.of(required(options, "acct"));
            Path outputAccountPath = Path.of(options.getOrDefault("out-acct", accountPath.toString()));
            Path outputTransactionPath = Path.of(required(options, "out-transact"));

            List<TranCatBalRecord> balances = new ArrayList<>();
            for (String record : FixedWidthFiles.readRecords(Path.of(required(options, "tcatbal")))) {
                balances.add(TranCatBalRecord.parse(record));
            }
            List<AccountRecord> accounts = new ArrayList<>();
            for (String record : FixedWidthFiles.readRecords(accountPath)) {
                accounts.add(AccountRecord.parse(record));
            }
            List<CardXrefRecord> xrefs = new ArrayList<>();
            for (String record : FixedWidthFiles.readRecords(Path.of(required(options, "xref")))) {
                xrefs.add(CardXrefRecord.parse(record));
            }
            List<DisclosureGroupRecord> rates = new ArrayList<>();
            for (String record : FixedWidthFiles.readRecords(Path.of(required(options, "discgrp")))) {
                rates.add(DisclosureGroupRecord.parse(record));
            }

            InterestCalculator.Options jobOptions = new InterestCalculator.Options(
                    required(options, "parm"),
                    Clock.systemDefaultZone(),
                    options.containsKey("emulate-final-account-quirk"));

            InterestCalculator calculator = new InterestCalculator(
                    jobOptions,
                    InterestCalculator.indexAccounts(accounts),
                    InterestCalculator.indexXrefsByAccount(xrefs),
                    InterestCalculator.indexDisclosureGroups(rates));

            InterestCalculator.Result result = calculator.run(balances);

            List<String> transactionRecords = new ArrayList<>();
            for (TransactionRecord transaction : calculator.transactions()) {
                transactionRecords.add(transaction.toRecord());
            }
            FixedWidthFiles.writeRecords(outputTransactionPath, transactionRecords);

            List<String> accountRecords = new ArrayList<>();
            for (AccountRecord account : calculator.updatedAccounts()) {
                accountRecords.add(account.toRecord());
            }
            FixedWidthFiles.writeRecords(outputAccountPath, accountRecords);

            out.printf("CATEGORY BALANCES READ  : %d%n", result.categoryBalancesRead());
            out.printf("INTEREST TRANSACTIONS   : %d%n", result.transactionsWritten());
            out.printf("ACCOUNTS UPDATED        : %d%n", result.accountsUpdated());
            out.println("END OF EXECUTION OF PROGRAM CBACT04C");
            return 0;
        } catch (AbendException e) {
            err.println(e.getMessage());
            err.println("ABENDING PROGRAM, CODE " + AbendException.ABEND_CODE);
            return 12;
        } catch (IOException e) {
            err.println("FILE ERROR: " + e.getMessage());
            err.println("ABENDING PROGRAM, CODE " + AbendException.ABEND_CODE);
            return 12;
        }
    }

    private static Map<String, String> parseArguments(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String argument = args[i];
            if (!argument.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument: " + argument);
            }
            String name = argument.substring(2);
            if (name.equals("emulate-final-account-quirk")) {
                options.put(name, "true");
                continue;
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("Missing value for --" + name);
            }
            options.put(name, args[++i]);
        }
        return options;
    }

    private static String required(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Missing required option --" + name);
        }
        return value;
    }
}
