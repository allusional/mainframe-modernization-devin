package com.carddemo.interest;

import com.carddemo.interest.records.AccountRecord;
import com.carddemo.interest.records.CardXrefRecord;
import com.carddemo.interest.records.DisclosureGroupRecord;
import com.carddemo.interest.records.TranCatBalRecord;
import com.carddemo.interest.records.TransactionRecord;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Java port of CBACT04C: monthly interest calculation for the CardDemo card portfolio.
 *
 * <p>The COBOL reads the transaction category balance KSDS in key sequence, so all rows for
 * one account arrive together and a change of account id settles the previous account. That
 * control break is reproduced here verbatim; see the class comments on each rule.
 */
public final class InterestCalculator {

    /** DEFAULT disclosure group, used when the account's own group has no rate on file. */
    public static final String DEFAULT_GROUP_ID = "DEFAULT";

    /** Interest transactions are always classified as transaction type 01 / category 0005. */
    public static final String INTEREST_TYPE_CODE = "01";
    public static final String INTEREST_CATEGORY_CODE = "0005";

    /** rate is a percentage per year, so one month of interest is balance * rate / (100 * 12). */
    private static final BigDecimal MONTHS_AND_PERCENT = BigDecimal.valueOf(1200);

    private final Options options;
    private final Map<Long, AccountRecord> accounts;
    private final Map<Long, CardXrefRecord> xrefsByAccount;
    private final Map<String, DisclosureGroupRecord> disclosureGroups;

    private final List<TransactionRecord> transactions = new ArrayList<>();

    private AccountRecord currentAccount;
    private CardXrefRecord currentXref;
    private BigDecimal accumulatedInterest = zero();
    private Long lastAccountId;
    private int categoryBalancesRead;
    private int accountsUpdated;
    private int transactionIdSuffix;

    public InterestCalculator(Options options,
                              Map<Long, AccountRecord> accounts,
                              Map<Long, CardXrefRecord> xrefsByAccount,
                              Map<String, DisclosureGroupRecord> disclosureGroups) {
        this.options = options;
        this.accounts = accounts;
        this.xrefsByAccount = xrefsByAccount;
        this.disclosureGroups = disclosureGroups;
    }

    /**
     * @param parmDate the ten character run parameter from the JCL PARM; it only ever forms
     *                 the prefix of the generated transaction ids
     * @param clock    source of the transaction timestamps
     * @param emulateFinalAccountQuirk when true, reproduce the COBOL behaviour where the last
     *                 account in the balance file never has its master record updated
     */
    public record Options(String parmDate, Clock clock, boolean emulateFinalAccountQuirk) {

        public Options {
            if (parmDate == null || parmDate.length() != 10) {
                throw new IllegalArgumentException("PARM date must be exactly 10 characters, got: " + parmDate);
            }
        }

        public static Options of(String parmDate) {
            return new Options(parmDate, Clock.systemDefaultZone(), false);
        }
    }

    public record Result(int categoryBalancesRead, int transactionsWritten, int accountsUpdated) {
    }

    /** Runs the job over {@code categoryBalances}, which must be in account id order. */
    public Result run(List<TranCatBalRecord> categoryBalances) {
        for (TranCatBalRecord balance : categoryBalances) {
            categoryBalancesRead++;
            if (lastAccountId == null || balance.accountId() != lastAccountId) {
                settleCurrentAccount();
                startAccount(balance.accountId());
            }
            DisclosureGroupRecord rate = findRate(balance);
            if (rate.annualInterestRate().signum() != 0) {
                computeInterest(balance, rate);
                computeFees();
            }
        }
        if (!options.emulateFinalAccountQuirk()) {
            settleCurrentAccount();
        }
        return new Result(categoryBalancesRead, transactions.size(), accountsUpdated);
    }

    public List<TransactionRecord> transactions() {
        return List.copyOf(transactions);
    }

    /** The account master in file order, with this run's updates applied. */
    public List<AccountRecord> updatedAccounts() {
        return List.copyOf(accounts.values());
    }

    private void startAccount(long accountId) {
        accumulatedInterest = zero();
        lastAccountId = accountId;
        currentAccount = accounts.get(accountId);
        if (currentAccount == null) {
            throw new AbendException("ACCOUNT NOT FOUND: " + accountId);
        }
        currentXref = xrefsByAccount.get(accountId);
        if (currentXref == null) {
            throw new AbendException("XREF ACCOUNT NOT FOUND: " + accountId);
        }
    }

    /** 1050-UPDATE-ACCOUNT. */
    private void settleCurrentAccount() {
        if (currentAccount == null) {
            return;
        }
        currentAccount.applyInterestAndCloseCycle(accumulatedInterest);
        accountsUpdated++;
    }

    /** 1200-GET-INTEREST-RATE and 1200-A-GET-DEFAULT-INT-RATE. */
    private DisclosureGroupRecord findRate(TranCatBalRecord balance) {
        String key = DisclosureGroupRecord.key(currentAccount.groupId(), balance.typeCode(), balance.categoryCode());
        DisclosureGroupRecord rate = disclosureGroups.get(key);
        if (rate != null) {
            return rate;
        }
        String defaultKey = DisclosureGroupRecord.key(DEFAULT_GROUP_ID, balance.typeCode(), balance.categoryCode());
        DisclosureGroupRecord defaultRate = disclosureGroups.get(defaultKey);
        if (defaultRate == null) {
            throw new AbendException("ERROR READING DEFAULT DISCLOSURE GROUP for " + defaultKey.trim());
        }
        return defaultRate;
    }

    /** 1300-COMPUTE-INTEREST. COBOL has no ROUNDED clause, so the result is truncated. */
    private void computeInterest(TranCatBalRecord balance, DisclosureGroupRecord rate) {
        BigDecimal monthlyInterest = balance.balance()
                .multiply(rate.annualInterestRate())
                .divide(MONTHS_AND_PERCENT, 2, RoundingMode.DOWN);
        accumulatedInterest = accumulatedInterest.add(monthlyInterest);
        transactions.add(buildTransaction(monthlyInterest));
    }

    /** 1400-COMPUTE-FEES is an empty stub in the COBOL; kept here to mark the gap. */
    private void computeFees() {
        // To be implemented (as in CBACT04C).
    }

    /** 1300-B-WRITE-TX. */
    private TransactionRecord buildTransaction(BigDecimal monthlyInterest) {
        transactionIdSuffix++;
        String transactionId = options.parmDate() + String.format("%06d", transactionIdSuffix);
        String timestamp = Db2Timestamp.now(options.clock());
        return new TransactionRecord(
                transactionId,
                INTEREST_TYPE_CODE,
                INTEREST_CATEGORY_CODE,
                "System",
                "Int. for a/c " + String.format("%011d", currentAccount.accountId()),
                monthlyInterest,
                0L,
                "",
                "",
                "",
                currentXref.cardNumber(),
                timestamp,
                timestamp);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2);
    }

    public static Map<Long, AccountRecord> indexAccounts(List<AccountRecord> records) {
        Map<Long, AccountRecord> index = new LinkedHashMap<>();
        for (AccountRecord record : records) {
            index.putIfAbsent(record.accountId(), record);
        }
        return index;
    }

    public static Map<Long, CardXrefRecord> indexXrefsByAccount(List<CardXrefRecord> records) {
        Map<Long, CardXrefRecord> index = new LinkedHashMap<>();
        for (CardXrefRecord record : records) {
            index.putIfAbsent(record.accountId(), record);
        }
        return index;
    }

    public static Map<String, DisclosureGroupRecord> indexDisclosureGroups(List<DisclosureGroupRecord> records) {
        Map<String, DisclosureGroupRecord> index = new LinkedHashMap<>();
        for (DisclosureGroupRecord record : records) {
            index.putIfAbsent(record.key(), record);
        }
        return index;
    }
}
