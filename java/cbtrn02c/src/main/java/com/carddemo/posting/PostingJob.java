package com.carddemo.posting;

import com.carddemo.interest.records.AccountRecord;
import com.carddemo.interest.records.CardXrefRecord;
import com.carddemo.interest.records.TransactionRecord;
import com.carddemo.posting.files.AccountMaster;
import com.carddemo.posting.files.CategoryBalanceFile;
import com.carddemo.posting.records.DailyTransactionRecord;
import com.carddemo.posting.records.RejectRecord;
import com.carddemo.posting.rules.PostingRules;
import com.carddemo.posting.rules.RejectReason;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The PROCEDURE DIVISION of CBTRN02C (CBTRN02C.cbl:193-234): read the daily feed once, and
 * for each record either post it to three files or write one reject record.
 *
 * <p>The validation ruleset lives in {@link PostingRules}; this class is only the sequencing,
 * the counters and the file updates.
 */
public final class PostingJob {

    private final PostingOptions options;
    private final PostingRules rules;
    private final Map<String, CardXrefRecord> cardXref;
    private final AccountMaster accounts;
    private final CategoryBalanceFile categoryBalances;
    private final Supplier<String> processedTimestamp;
    private final Consumer<String> display;

    private final Map<String, TransactionRecord> transactionMaster = new TreeMap<>();
    private final List<RejectRecord> rejects = new ArrayList<>();
    private final Map<String, Long> bucketsCreated = new LinkedHashMap<>();
    private final List<String> messages = new ArrayList<>();

    public PostingJob(PostingOptions options, Iterable<CardXrefRecord> cardXrefRecords, AccountMaster accounts,
                      CategoryBalanceFile categoryBalances, Supplier<String> processedTimestamp) {
        this(options, cardXrefRecords, accounts, categoryBalances, processedTimestamp, message -> { });
    }

    public PostingJob(PostingOptions options, Iterable<CardXrefRecord> cardXrefRecords, AccountMaster accounts,
                      CategoryBalanceFile categoryBalances, Supplier<String> processedTimestamp,
                      Consumer<String> display) {
        this.options = options;
        this.display = display;
        this.rules = new PostingRules(options);
        this.cardXref = new LinkedHashMap<>();
        for (CardXrefRecord xref : cardXrefRecords) {
            this.cardXref.put(xref.cardNumber(), xref);
        }
        this.accounts = accounts;
        this.categoryBalances = categoryBalances;
        this.processedTimestamp = processedTimestamp;
    }

    public PostingResult run(Iterable<DailyTransactionRecord> dailyTransactions) {
        long processed = 0;
        long rejected = 0;
        Map<RejectReason, Long> byReason = new EnumMap<>(RejectReason.class);

        for (DailyTransactionRecord transaction : dailyTransactions) {
            processed++;                                              // :206, counted before validation
            Optional<RejectReason> failure = validate(transaction);
            if (failure.isEmpty()) {
                failure = post(transaction);                          // :212
            }
            if (failure.isPresent()) {
                rejected++;                                           // :214
                rejects.add(new RejectRecord(transaction.raw(), failure.get()));
                byReason.merge(failure.get(), 1L, Long::sum);
            }
        }
        return new PostingResult(processed, rejected, byReason, bucketsCreated);
    }

    /**
     * 1500-VALIDATE-TRAN (CBTRN02C.cbl:370-421). The cross reference lookup gates the account
     * lookup, but the credit limit and expiry checks both run - see finding D8 for why that
     * matters and what {@code lastRejectReasonWins} selects between.
     */
    private Optional<RejectReason> validate(DailyTransactionRecord transaction) {
        CardXrefRecord xref = cardXref.get(transaction.cardNumber());
        if (!rules.cardIsKnown(xref != null)) {
            return Optional.of(RejectReason.INVALID_CARD_NUMBER);
        }

        Optional<AccountRecord> account = accounts.read(xref.accountId());
        if (!rules.accountIsKnown(account.isPresent())) {
            return Optional.of(RejectReason.ACCOUNT_NOT_FOUND);
        }

        List<RejectReason> failures = new ArrayList<>();
        if (!rules.withinCreditLimit(account.get(), transaction.amount())) {
            failures.add(RejectReason.OVERLIMIT);
        }
        if (!rules.notAfterExpiration(account.get(), transaction)) {
            failures.add(RejectReason.AFTER_EXPIRATION);
        }
        if (failures.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(options.lastRejectReasonWins()
                ? failures.get(failures.size() - 1)
                : failures.get(0));
    }

    /**
     * 2000-POST-TRANSACTION (CBTRN02C.cbl:424-444). The COBOL updates the category balance,
     * then the account, then the transaction master, and ignores a failed account rewrite
     * (D1). Corrected, the account goes first so that a failed rewrite leaves nothing behind.
     */
    private Optional<RejectReason> post(DailyTransactionRecord transaction) {
        CardXrefRecord xref = cardXref.get(transaction.cardNumber());
        AccountRecord account = accounts.read(xref.accountId()).orElseThrow();

        if (!options.lostAccountUpdateIsSilent()) {
            if (!applyToAccount(account, transaction.amount())) {
                return Optional.of(RejectReason.ACCOUNT_REWRITE_FAILED);
            }
            if (transactionMaster.containsKey(transaction.transactionId())) {
                return duplicateTransactionId(transaction);
            }
            applyToCategoryBalance(xref.accountId(), transaction);
        } else {
            applyToCategoryBalance(xref.accountId(), transaction);    // :440
            applyToAccount(account, transaction.amount());            // :441, result discarded (D1)
            if (transactionMaster.containsKey(transaction.transactionId())) {
                return duplicateTransactionId(transaction);
            }
        }

        transactionMaster.put(transaction.transactionId(),            // :442, 2900-WRITE-TRANSACTION-FILE
                transaction.toPostedTransaction(processedTimestamp.get()));
        return Optional.empty();
    }

    /**
     * 2800-UPDATE-ACCOUNT-REC (CBTRN02C.cbl:545-559). Rule R17: the running balance always
     * moves by the signed amount; the split between the two cycle-to-date fields is by sign,
     * with zero counting as a credit.
     */
    private boolean applyToAccount(AccountRecord account, BigDecimal amount) {
        account.addToCurrentBalance(amount);
        if (amount.signum() >= 0) {
            account.addToCurrentCycleCredit(amount);
        } else {
            account.addToCurrentCycleDebit(amount);
        }
        return accounts.rewrite(account);
    }

    /** 2700-UPDATE-TCATBAL (CBTRN02C.cbl:467-541). */
    private void applyToCategoryBalance(long accountId, DailyTransactionRecord transaction) {
        boolean created = categoryBalances.addToBalance(accountId, transaction.typeCode(),
                transaction.categoryCode(), transaction.amount());
        if (created) {
            String key = CategoryBalanceFile.key(accountId, transaction.typeCode(), transaction.categoryCode());
            bucketsCreated.merge(key, 1L, Long::sum);
            String message = "TCATBAL record not found for key : " + key + ".. Creating.";   // :476-477
            messages.add(message);
            display.accept(message);
        }
    }

    /**
     * D2. A KSDS WRITE of a key that is already there gives file status 22, which
     * 2900-WRITE-TRANSACTION-FILE treats as fatal (CBTRN02C.cbl:566-578).
     */
    private Optional<RejectReason> duplicateTransactionId(DailyTransactionRecord transaction) {
        if (options.abendOnDuplicateTransactionId()) {
            throw new AbendException("ERROR WRITING TO TRANSACTION FILE\nFILE STATUS IS: NNNN0022"
                    + " (duplicate TRAN-ID " + transaction.transactionId() + ")");
        }
        return Optional.of(RejectReason.DUPLICATE_TRANSACTION_ID);
    }

    /** The transaction master in key order, which is how a KSDS reads back. */
    public List<TransactionRecord> postedTransactions() {
        return new ArrayList<>(transactionMaster.values());
    }

    /** The reject file in the order the records were written. */
    public List<RejectRecord> rejectedTransactions() {
        return List.copyOf(rejects);
    }

    /** The "TCATBAL record not found ... Creating." lines the COBOL displays. */
    public List<String> displayedMessages() {
        return List.copyOf(messages);
    }
}
