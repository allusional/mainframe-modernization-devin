package com.carddemo.posting;

import com.carddemo.interest.Db2Timestamp;
import com.carddemo.interest.io.FixedWidthFiles;
import com.carddemo.interest.records.AccountRecord;
import com.carddemo.interest.records.CardXrefRecord;
import com.carddemo.interest.records.TranCatBalRecord;
import com.carddemo.interest.records.TransactionRecord;
import com.carddemo.posting.files.AccountMaster;
import com.carddemo.posting.files.CategoryBalanceFile;
import com.carddemo.posting.records.DailyTransactionRecord;
import com.carddemo.posting.records.RejectRecord;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Command line entry point, the equivalent of the POSTTRAN job step
 * {@code //STEP15 EXEC PGM=CBTRN02C}. The options are named after the DD statements in
 * {@code app/jcl/POSTTRAN.jcl}; the program itself takes no PARM, and neither does this.
 */
public final class Cbtrn02c {

    private static final String USAGE = """
            Usage: cbtrn02c --dalytran <file> --xreffile <file> --acctfile <file> --tcatbalf <file> \\
                            --tranfile <file> --dalyrejs <file> [--out-acctfile <file>] \\
                            [--out-tcatbalf <file>] [--bug-for-bug] [flag ...]

              --dalytran        the day's transaction feed, 350 byte records   (DD DALYTRAN, CVTRA06Y)
              --xreffile        card cross reference, 50 byte records          (DD XREFFILE, CVACT03Y)
              --acctfile        account master, 300 byte records               (DD ACCTFILE, CVACT01Y)
              --tcatbalf        transaction category balances, 50 byte records (DD TCATBALF, CVTRA01Y)
              --tranfile        transaction master to write, 350 byte records  (DD TRANFILE, CVTRA05Y)
              --dalyrejs        rejects to write, 430 byte records             (DD DALYREJS)
              --out-acctfile    updated account master; defaults to rewriting --acctfile in place,
                                which is what the COBOL does (it opens ACCTFILE I-O)
              --out-tcatbalf    updated category balances; defaults to rewriting --tcatbalf in place

            Behaviour flags. Every one of these defaults to the corrected behaviour; each
            reproduces a documented CBTRN02C defect (see CBTRN02C-EXPLAINED.md) when given:

              --bug-for-bug                          all of the emulation flags below at once
              --emulate-lost-account-update          D1: ignore a failed account rewrite silently
              --emulate-abend-on-duplicate-tran-id   D2: abend instead of rejecting a duplicate id
              --emulate-refunds-count-against-limit  D4: CYC-CREDIT - CYC-DEBIT, so refunds use up limit
              --emulate-temp-balance-truncation      D5: WS-TEMP-BAL as PIC S9(09)V99, no ON SIZE ERROR
              --emulate-last-reject-reason-wins      D8: expiry overwrites over-limit as the reason

            Not a defect fix, opt in only:

              --include-current-balance-in-limit-check
                                D3: also count ACCT-CURR-BAL in the over-limit test. The COBOL does
                                not, and this port cannot tell whether that is deliberate, so the
                                default matches the COBOL.
            """;

    private Cbtrn02c() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err, Clock.systemDefaultZone()));
    }

    static int run(String[] args, PrintStream out, PrintStream err, Clock clock) {
        Map<String, String> arguments;
        PostingOptions options;
        try {
            arguments = parseArguments(args);
            options = optionsFrom(arguments);
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            err.println();
            err.println(USAGE);
            return 12;
        }

        out.println("START OF EXECUTION OF PROGRAM CBTRN02C");
        try {
            Path accountPath = Path.of(required(arguments, "acctfile"));
            Path categoryBalancePath = Path.of(required(arguments, "tcatbalf"));

            List<DailyTransactionRecord> feed = new ArrayList<>();
            for (String record : FixedWidthFiles.readRecords(Path.of(required(arguments, "dalytran")))) {
                feed.add(DailyTransactionRecord.parse(record));
            }
            List<CardXrefRecord> xrefs = new ArrayList<>();
            for (String record : FixedWidthFiles.readRecords(Path.of(required(arguments, "xreffile")))) {
                xrefs.add(CardXrefRecord.parse(record));
            }
            List<AccountRecord> accountRecords = new ArrayList<>();
            for (String record : FixedWidthFiles.readRecords(accountPath)) {
                accountRecords.add(AccountRecord.parse(record));
            }
            List<TranCatBalRecord> balances = new ArrayList<>();
            for (String record : FixedWidthFiles.readRecords(categoryBalancePath)) {
                balances.add(TranCatBalRecord.parse(record));
            }

            AccountMaster accounts = new AccountMaster(accountRecords);
            CategoryBalanceFile categoryBalances = new CategoryBalanceFile(balances);
            PostingJob job = new PostingJob(options, xrefs, accounts, categoryBalances,
                    () -> Db2Timestamp.now(clock), out::println);

            PostingResult result = job.run(feed);

            List<String> transactionRecords = new ArrayList<>();
            for (TransactionRecord transaction : job.postedTransactions()) {
                transactionRecords.add(transaction.toRecord());
            }
            FixedWidthFiles.writeRecords(Path.of(required(arguments, "tranfile")), transactionRecords);

            List<String> rejectRecords = new ArrayList<>();
            for (RejectRecord reject : job.rejectedTransactions()) {
                rejectRecords.add(reject.toRecord());
            }
            FixedWidthFiles.writeRecords(Path.of(required(arguments, "dalyrejs")), rejectRecords);

            List<String> updatedAccounts = new ArrayList<>();
            accounts.inKeyOrder().values().forEach(account -> updatedAccounts.add(account.toRecord()));
            FixedWidthFiles.writeRecords(
                    Path.of(arguments.getOrDefault("out-acctfile", accountPath.toString())), updatedAccounts);

            List<String> updatedBalances = new ArrayList<>();
            categoryBalances.inKeyOrder().values().forEach(bucket -> updatedBalances.add(bucket.toRecord()));
            FixedWidthFiles.writeRecords(
                    Path.of(arguments.getOrDefault("out-tcatbalf", categoryBalancePath.toString())), updatedBalances);

            out.printf("TRANSACTIONS PROCESSED :%09d%n", result.transactionsProcessed());
            out.printf("TRANSACTIONS REJECTED  :%09d%n", result.transactionsRejected());
            if (!options.equals(PostingOptions.bugForBug())) {
                out.printf("TRANSACTIONS POSTED    :%09d%n", result.transactionsPosted());
            }
            out.println("END OF EXECUTION OF PROGRAM CBTRN02C");
            return result.returnCode();
        } catch (AbendException e) {
            err.println(e.getMessage());
            err.println("ABENDING PROGRAM, CODE " + AbendException.ABEND_CODE);
            return 12;
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            err.println();
            err.println(USAGE);
            return 12;
        } catch (IOException e) {
            err.println("FILE ERROR: " + e.getMessage());
            err.println("ABENDING PROGRAM, CODE " + AbendException.ABEND_CODE);
            return 12;
        }
    }

    static PostingOptions optionsFrom(Map<String, String> arguments) {
        PostingOptions options = arguments.containsKey("bug-for-bug")
                ? PostingOptions.bugForBug()
                : PostingOptions.corrected();
        if (arguments.containsKey("emulate-lost-account-update")) {
            options = options.withLostAccountUpdateIsSilent(true);
        }
        if (arguments.containsKey("emulate-abend-on-duplicate-tran-id")) {
            options = options.withAbendOnDuplicateTransactionId(true);
        }
        if (arguments.containsKey("emulate-refunds-count-against-limit")) {
            options = options.withRefundsCountAgainstLimit(true);
        }
        if (arguments.containsKey("emulate-temp-balance-truncation")) {
            options = options.withTruncateTempBalance(true);
        }
        if (arguments.containsKey("emulate-last-reject-reason-wins")) {
            options = options.withLastRejectReasonWins(true);
        }
        if (arguments.containsKey("include-current-balance-in-limit-check")) {
            options = options.withIncludeCurrentBalanceInCreditLimitCheck(true);
        }
        return options;
    }

    private static final List<String> FLAGS = List.of(
            "bug-for-bug",
            "emulate-lost-account-update",
            "emulate-abend-on-duplicate-tran-id",
            "emulate-refunds-count-against-limit",
            "emulate-temp-balance-truncation",
            "emulate-last-reject-reason-wins",
            "include-current-balance-in-limit-check");

    static Map<String, String> parseArguments(String[] args) {
        Map<String, String> arguments = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument: " + args[i]);
            }
            String name = args[i].substring(2);
            if (FLAGS.contains(name)) {
                arguments.put(name, "true");
                continue;
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("Missing value for --" + name);
            }
            arguments.put(name, args[++i]);
        }
        return arguments;
    }

    private static String required(Map<String, String> arguments, String name) {
        String value = arguments.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Missing required option --" + name);
        }
        return value;
    }
}
