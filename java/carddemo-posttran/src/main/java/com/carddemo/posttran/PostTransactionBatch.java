package com.carddemo.posttran;

import java.math.BigDecimal;

/** Java port of the CBTRN02C (POSTTRAN) PROCEDURE DIVISION. */
public class PostTransactionBatch {

    private final DailyTransactionReader dailyTransactions;
    private final XrefRepository xrefs;
    private final AccountRepository accounts;
    private final TranCatBalanceRepository tranCatBalances;
    private final TransactionWriter transactionWriter;
    private final RejectWriter rejectWriter;
    private final Db2Timestamp timestamps;

    private long transactionCount;
    private long rejectCount;

    public PostTransactionBatch(DailyTransactionReader dailyTransactions,
                                XrefRepository xrefs,
                                AccountRepository accounts,
                                TranCatBalanceRepository tranCatBalances,
                                TransactionWriter transactionWriter,
                                RejectWriter rejectWriter,
                                Db2Timestamp timestamps) {
        this.dailyTransactions = dailyTransactions;
        this.xrefs = xrefs;
        this.accounts = accounts;
        this.tranCatBalances = tranCatBalances;
        this.transactionWriter = transactionWriter;
        this.rejectWriter = rejectWriter;
        this.timestamps = timestamps;
    }

    /** PROCEDURE DIVISION main loop; returns RETURN-CODE (4 when anything was rejected). */
    public int run() {
        for (var daily = dailyTransactions.next(); daily.isPresent(); daily = dailyTransactions.next()) {
            DailyTransaction record = daily.get();
            transactionCount++;
            ValidationResult validation = validate(record);
            if (validation.isValid()) {
                postTransaction(record, validation);
            } else {
                rejectCount++;
                writeReject(record, validation);
            }
        }
        return rejectCount > 0 ? 4 : 0;
    }

    /** 1500-VALIDATE-TRAN. */
    ValidationResult validate(DailyTransaction daily) {
        var xref = xrefs.findByCardNum(daily.getCardNum());
        if (xref.isEmpty()) {
            return new ValidationResult(100, "INVALID CARD NUMBER FOUND", null, null);
        }
        return lookupAccount(daily, xref.get());
    }

    /** 1500-B-LOOKUP-ACCT. */
    private ValidationResult lookupAccount(DailyTransaction daily, CardXref xref) {
        var found = accounts.findById(xref.getAcctId());
        if (found.isEmpty()) {
            return new ValidationResult(101, "ACCOUNT RECORD NOT FOUND", xref, null);
        }
        Account account = found.get();

        int failReason = 0;
        String failReasonDesc = "";

        BigDecimal tempBal = account.getCurrCycCredit()
                .subtract(account.getCurrCycDebit())
                .add(daily.getAmt());
        if (account.getCreditLimit().compareTo(tempBal) < 0) {
            failReason = 102;
            failReasonDesc = "OVERLIMIT TRANSACTION";
        }
        String origDate = daily.getOrigTs().substring(0, 10);
        if (account.getExpirationDate().compareTo(origDate) < 0) {
            failReason = 103;
            failReasonDesc = "TRANSACTION RECEIVED AFTER ACCT EXPIRATION";
        }
        return failReason == 0
                ? new ValidationResult(0, "", xref, account)
                : new ValidationResult(failReason, failReasonDesc, xref, account);
    }

    /** 2000-POST-TRANSACTION. */
    void postTransaction(DailyTransaction daily, ValidationResult validation) {
        Transaction transaction = new Transaction();
        transaction.setId(daily.getId());
        transaction.setTypeCd(daily.getTypeCd());
        transaction.setCatCd(daily.getCatCd());
        transaction.setSource(daily.getSource());
        transaction.setDesc(daily.getDesc());
        transaction.setAmt(daily.getAmt());
        transaction.setMerchantId(daily.getMerchantId());
        transaction.setMerchantName(daily.getMerchantName());
        transaction.setMerchantCity(daily.getMerchantCity());
        transaction.setMerchantZip(daily.getMerchantZip());
        transaction.setCardNum(daily.getCardNum());
        transaction.setOrigTs(daily.getOrigTs());
        transaction.setProcTs(timestamps.now());

        updateTranCatBalance(daily, validation.getXref().getAcctId());
        updateAccount(validation.getAccount(), daily.getAmt());
        transactionWriter.write(transaction);
    }

    /** 2700-UPDATE-TCATBAL / 2700-A-CREATE-TCATBAL-REC / 2700-B-UPDATE-TCATBAL-REC. */
    void updateTranCatBalance(DailyTransaction daily, long acctId) {
        TranCatBalanceKey key = new TranCatBalanceKey(acctId, daily.getTypeCd(), daily.getCatCd());
        var existing = tranCatBalances.find(key);
        if (existing.isEmpty()) {
            tranCatBalances.create(new TranCatBalance(acctId, daily.getTypeCd(), daily.getCatCd(), daily.getAmt()));
        } else {
            TranCatBalance record = existing.get();
            record.setBalance(record.getBalance().add(daily.getAmt()));
            tranCatBalances.update(record);
        }
    }

    /** 2800-UPDATE-ACCOUNT-REC. */
    void updateAccount(Account account, BigDecimal amount) {
        account.setCurrBal(account.getCurrBal().add(amount));
        if (amount.signum() >= 0) {
            account.setCurrCycCredit(account.getCurrCycCredit().add(amount));
        } else {
            account.setCurrCycDebit(account.getCurrCycDebit().add(amount));
        }
        accounts.update(account);
    }

    /** 2500-WRITE-REJECT-REC. */
    private void writeReject(DailyTransaction daily, ValidationResult validation) {
        rejectWriter.write(new RejectRecord(daily, validation.getFailReason(), validation.getFailReasonDesc()));
    }

    /** WS-TRANSACTION-COUNT. */
    public long getTransactionCount() {
        return transactionCount;
    }

    /** WS-REJECT-COUNT. */
    public long getRejectCount() {
        return rejectCount;
    }
}
