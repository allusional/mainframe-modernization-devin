package com.carddemo.cbtrn02c.service;

import com.carddemo.cbtrn02c.model.AccountRecord;
import com.carddemo.cbtrn02c.model.CardXrefRecord;
import com.carddemo.cbtrn02c.model.DailyTransactionRecord;
import com.carddemo.cbtrn02c.model.TranCatBalRecord;
import com.carddemo.cbtrn02c.model.TransactionRecord;
import com.carddemo.cbtrn02c.repository.AccountRepository;
import com.carddemo.cbtrn02c.repository.CardXrefRepository;
import com.carddemo.cbtrn02c.repository.TranCatBalRepository;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Java equivalent of the core business logic of COBOL program CBTRN02C.
 *
 * <p>Validates each daily transaction against the card cross-reference and account
 * master, and — when valid — posts it: updating the transaction category balance,
 * updating the account balances, and producing the posted transaction record.
 */
public class TransactionPostingService {

    private final CardXrefRepository xrefRepository;
    private final AccountRepository accountRepository;
    private final TranCatBalRepository tranCatBalRepository;
    private final ProcessingTimestampProvider timestampProvider;

    public TransactionPostingService(CardXrefRepository xrefRepository,
                                     AccountRepository accountRepository,
                                     TranCatBalRepository tranCatBalRepository,
                                     ProcessingTimestampProvider timestampProvider) {
        this.xrefRepository = xrefRepository;
        this.accountRepository = accountRepository;
        this.tranCatBalRepository = tranCatBalRepository;
        this.timestampProvider = timestampProvider;
    }

    /**
     * Processes a single daily transaction, mirroring the main loop body of CBTRN02C:
     * validate, then post or reject.
     */
    public PostingOutcome process(DailyTransactionRecord daly) {
        Optional<CardXrefRecord> xref = xrefRepository.findByCardNumber(daly.getCardNumber());
        if (xref.isEmpty()) {
            return PostingOutcome.rejected(daly,
                    ValidationResult.reject(ValidationResult.INVALID_CARD_NUMBER, "INVALID CARD NUMBER FOUND"));
        }

        long accountId = xref.get().getAccountId();
        Optional<AccountRecord> accountLookup = accountRepository.findById(accountId);
        if (accountLookup.isEmpty()) {
            return PostingOutcome.rejected(daly,
                    ValidationResult.reject(ValidationResult.ACCOUNT_NOT_FOUND, "ACCOUNT RECORD NOT FOUND"));
        }

        AccountRecord account = accountLookup.get();
        ValidationResult validation = validateAgainstAccount(daly, account);
        if (!validation.isValid()) {
            return PostingOutcome.rejected(daly, validation);
        }

        return postTransaction(daly, accountId, account);
    }

    /**
     * Credit-limit and expiration checks from paragraph 1500-B-LOOKUP-ACCT. Both checks
     * are evaluated in sequence; when both fail the expiration reason (103) is the one
     * that survives, exactly as the COBOL MOVEs overwrite the reason field.
     */
    private ValidationResult validateAgainstAccount(DailyTransactionRecord daly, AccountRecord account) {
        ValidationResult result = ValidationResult.ok();

        BigDecimal tempBal = account.getCurrentCycleCredit()
                .subtract(account.getCurrentCycleDebit())
                .add(daly.getAmount());
        if (account.getCreditLimit().compareTo(tempBal) < 0) {
            result = ValidationResult.reject(ValidationResult.OVERLIMIT, "OVERLIMIT TRANSACTION");
        }

        String origDate = daly.getOriginalTimestamp().substring(0, 10);
        if (account.getExpirationDate().compareTo(origDate) < 0) {
            result = ValidationResult.reject(ValidationResult.AFTER_EXPIRATION,
                    "TRANSACTION RECEIVED AFTER ACCT EXPIRATION");
        }

        return result;
    }

    private PostingOutcome postTransaction(DailyTransactionRecord daly, long accountId, AccountRecord account) {
        TransactionRecord tran = TransactionRecord.fromDailyTransaction(daly, timestampProvider.currentTimestamp());
        updateTransactionCategoryBalance(daly, accountId);
        updateAccount(daly, account);
        return PostingOutcome.posted(daly, tran);
    }

    /** Paragraph 2700-UPDATE-TCATBAL (with 2700-A create / 2700-B update). */
    private void updateTransactionCategoryBalance(DailyTransactionRecord daly, long accountId) {
        String key = TranCatBalRecord.key(accountId, daly.getTypeCode(), daly.getCategoryCode());
        Optional<TranCatBalRecord> existing = tranCatBalRepository.findByKey(key);
        TranCatBalRecord record = existing.orElseGet(
                () -> TranCatBalRecord.create(accountId, daly.getTypeCode(), daly.getCategoryCode()));
        record.setBalance(record.getBalance().add(daly.getAmount()));
        tranCatBalRepository.save(record);
    }

    /** Paragraph 2800-UPDATE-ACCOUNT-REC. */
    private void updateAccount(DailyTransactionRecord daly, AccountRecord account) {
        BigDecimal amount = daly.getAmount();
        account.setCurrentBalance(account.getCurrentBalance().add(amount));
        if (amount.signum() >= 0) {
            account.setCurrentCycleCredit(account.getCurrentCycleCredit().add(amount));
        } else {
            account.setCurrentCycleDebit(account.getCurrentCycleDebit().add(amount));
        }
        if (!accountRepository.rewrite(account)) {
            throw new BatchAbendException("Account record not found on rewrite: " + account.getAccountId());
        }
    }
}
