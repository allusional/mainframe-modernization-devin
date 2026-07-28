package com.aws.carddemo.cbact04c.service;

import com.aws.carddemo.cbact04c.model.AccountRecord;
import com.aws.carddemo.cbact04c.model.CardXrefRecord;
import com.aws.carddemo.cbact04c.model.DisclosureGroupRecord;
import com.aws.carddemo.cbact04c.model.TranCatBalRecord;
import com.aws.carddemo.cbact04c.model.TransactionRecord;
import com.aws.carddemo.cbact04c.repository.AccountRepository;
import com.aws.carddemo.cbact04c.repository.CardXrefRepository;
import com.aws.carddemo.cbact04c.repository.DisclosureGroupRepository;
import com.aws.carddemo.cbact04c.repository.TransactionWriter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Java equivalent of the COBOL batch program {@code CBACT04C} (interest
 * calculator). Iterates the Transaction Category Balance file (assumed ordered
 * by account, as its VSAM key implies), accumulates monthly interest per
 * account, writes an interest transaction for every non-zero amount, and posts
 * the accumulated interest back to the account master when the account changes.
 *
 * <p><strong>Faithfully reproduced quirk:</strong> in the original COBOL the
 * {@code ELSE PERFORM 1050-UPDATE-ACCOUNT} branch of the main driver loop is
 * unreachable (the loop exits as soon as END-OF-FILE = 'Y'), so the accumulated
 * interest for the <em>last</em> account group is never posted to the account
 * master, even though its interest transactions are still written. This
 * behavior is preserved here.
 */
public class InterestCalculatorService {

    private static final int MONEY_SCALE = 2;
    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(MONEY_SCALE);
    private static final BigDecimal MONTHS_TIMES_PERCENT = BigDecimal.valueOf(1200);
    private static final String DEFAULT_GROUP_ID = "DEFAULT";

    private final AccountRepository accountRepository;
    private final CardXrefRepository cardXrefRepository;
    private final DisclosureGroupRepository disclosureGroupRepository;
    private final TransactionWriter transactionWriter;
    private final TimestampProvider timestampProvider;

    public InterestCalculatorService(AccountRepository accountRepository,
                                     CardXrefRepository cardXrefRepository,
                                     DisclosureGroupRepository disclosureGroupRepository,
                                     TransactionWriter transactionWriter,
                                     TimestampProvider timestampProvider) {
        this.accountRepository = accountRepository;
        this.cardXrefRepository = cardXrefRepository;
        this.disclosureGroupRepository = disclosureGroupRepository;
        this.transactionWriter = transactionWriter;
        this.timestampProvider = timestampProvider;
    }

    /**
     * Run the interest calculation over the supplied Transaction Category
     * Balance records (PROCEDURE DIVISION main driver).
     *
     * @param balances  TCATBAL records in account order
     * @param parmDate  the run date passed as JCL PARM (used as the transaction id prefix)
     */
    public InterestCalculationResult run(List<TranCatBalRecord> balances, String parmDate) {
        boolean firstTime = true;
        Long lastAccountId = null;
        BigDecimal totalInterest = ZERO_MONEY;
        long recordCount = 0;
        long transactionSuffix = 0;

        AccountRecord currentAccount = null;
        CardXrefRecord currentXref = null;

        for (TranCatBalRecord balance : balances) {
            recordCount++;

            if (lastAccountId == null || balance.getAccountId() != lastAccountId) {
                if (!firstTime) {
                    updateAccount(currentAccount, totalInterest);
                } else {
                    firstTime = false;
                }
                totalInterest = ZERO_MONEY;
                lastAccountId = balance.getAccountId();
                currentAccount = readAccount(balance.getAccountId());
                currentXref = readXref(balance.getAccountId());
            }

            BigDecimal interestRate = getInterestRate(
                    currentAccount.getGroupId(), balance.getTypeCode(), balance.getCategoryCode());

            if (interestRate.signum() != 0) {
                BigDecimal monthlyInterest = computeMonthlyInterest(balance.getBalance(), interestRate);
                totalInterest = totalInterest.add(monthlyInterest);
                transactionSuffix++;
                writeInterestTransaction(currentAccount, currentXref, monthlyInterest,
                        parmDate, transactionSuffix);
                computeFees();
            }
        }
        // 1050-UPDATE-ACCOUNT for the final account is intentionally NOT invoked here:
        // see the class-level note on the original program's unreachable ELSE branch.

        return new InterestCalculationResult(recordCount, transactionSuffix);
    }

    /** 1300-COMPUTE-INTEREST: (balance * rate) / 1200, truncated to 2 decimals (COBOL default). */
    BigDecimal computeMonthlyInterest(BigDecimal balance, BigDecimal interestRate) {
        return balance.multiply(interestRate)
                .divide(MONTHS_TIMES_PERCENT, MONEY_SCALE, RoundingMode.DOWN);
    }

    /** 1050-UPDATE-ACCOUNT: post accumulated interest and reset the current-cycle totals. */
    private void updateAccount(AccountRecord account, BigDecimal totalInterest) {
        account.setCurrentBalance(account.getCurrentBalance().add(totalInterest));
        account.setCurrentCycleCredit(ZERO_MONEY);
        account.setCurrentCycleDebit(ZERO_MONEY);
        accountRepository.rewrite(account);
    }

    /** 1400-COMPUTE-FEES: marked "To be implemented" in the original program; no-op. */
    private void computeFees() {
        // Intentionally empty to preserve original behavior.
    }

    /** 1200-GET-INTEREST-RATE, including the '23' fallback to the DEFAULT group (1200-A). */
    BigDecimal getInterestRate(String accountGroupId, String tranTypeCode, int tranCategoryCode) {
        Optional<DisclosureGroupRecord> primary =
                disclosureGroupRepository.read(accountGroupId, tranTypeCode, tranCategoryCode);
        if (primary.isPresent()) {
            return primary.get().getInterestRate();
        }
        DisclosureGroupRecord fallback = disclosureGroupRepository
                .read(DEFAULT_GROUP_ID, tranTypeCode, tranCategoryCode)
                .orElseThrow(() -> new AbendException("ERROR READING DEFAULT DISCLOSURE GROUP"));
        return fallback.getInterestRate();
    }

    /** 1100-GET-ACCT-DATA: read account master; missing account abends. */
    private AccountRecord readAccount(long accountId) {
        return accountRepository.read(accountId)
                .orElseThrow(() -> new AbendException("ERROR READING ACCOUNT FILE, ACCT=" + accountId));
    }

    /** 1110-GET-XREF-DATA: read card cross-reference; missing entry abends. */
    private CardXrefRecord readXref(long accountId) {
        return cardXrefRepository.readByAccountId(accountId)
                .orElseThrow(() -> new AbendException("ERROR READING XREF FILE, ACCT=" + accountId));
    }

    /** 1300-B-WRITE-TX: build and write the interest transaction record. */
    private void writeInterestTransaction(AccountRecord account, CardXrefRecord xref,
                                          BigDecimal monthlyInterest, String parmDate,
                                          long transactionSuffix) {
        String transactionId = buildTransactionId(parmDate, transactionSuffix);
        String description = "Int. for a/c " + String.format("%011d", account.getAccountId());
        String timestamp = timestampProvider.currentTimestamp();

        TransactionRecord transaction = new TransactionRecord(
                transactionId,
                "01",
                5,
                "System",
                description,
                monthlyInterest,
                0L,
                "",
                "",
                "",
                xref.getCardNumber(),
                timestamp,
                timestamp);
        transactionWriter.write(transaction);
    }

    /** STRING PARM-DATE, WS-TRANID-SUFFIX -> TRAN-ID (X(16)). */
    private String buildTransactionId(String parmDate, long transactionSuffix) {
        String id = (parmDate == null ? "" : parmDate) + String.format("%06d", transactionSuffix);
        return id.length() > 16 ? id.substring(0, 16) : id;
    }
}
