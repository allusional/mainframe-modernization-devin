package com.carddemo.posting;

import com.carddemo.interest.records.AccountRecord;
import com.carddemo.interest.records.CardXrefRecord;
import com.carddemo.interest.records.TranCatBalRecord;
import com.carddemo.interest.records.TransactionRecord;
import com.carddemo.posting.files.AccountMaster;
import com.carddemo.posting.files.CategoryBalanceFile;
import com.carddemo.posting.records.DailyTransactionRecord;
import com.carddemo.posting.records.RejectRecord;
import com.carddemo.posting.rules.RejectReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.carddemo.posting.Fixtures.CARD;
import static com.carddemo.posting.Fixtures.ACCOUNT;
import static com.carddemo.posting.Fixtures.account;
import static com.carddemo.posting.Fixtures.categoryBalance;
import static com.carddemo.posting.Fixtures.dailyTransaction;
import static com.carddemo.posting.Fixtures.xref;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The mainline: what reaches each output file, what the counters say, what RC comes back. */
class PostingJobTest {

    private static final String TIMESTAMP = "2024-06-02-01.02.03.450000";

    @Test
    @DisplayName("happy path: three files change and nothing is rejected")
    void postsToEveryOutputFile() {
        AccountMaster accounts = new AccountMaster(List.of(account()));
        CategoryBalanceFile balances = new CategoryBalanceFile(List.of(
                categoryBalance(ACCOUNT, "01", "0001", "40.00")));
        PostingJob job = job(accounts, balances, PostingOptions.corrected());

        PostingResult result = job.run(List.of(dailyTransaction("100.00")));

        assertEquals(1, result.transactionsProcessed());
        assertEquals(0, result.transactionsRejected());
        assertEquals(1, result.transactionsPosted());
        assertEquals(0, result.returnCode());

        // TRANFILE: one record, every field copied across and only TRAN-PROC-TS replaced.
        TransactionRecord posted = job.postedTransactions().get(0);
        assertEquals(1, job.postedTransactions().size());
        assertEquals("0000000000000001", posted.transactionId());
        assertEquals(CARD, posted.cardNumber());
        assertEquals(new BigDecimal("100.00"), posted.amount());
        assertEquals("2024-06-01-12.00.00.000000", posted.originTimestamp());
        assertEquals(TIMESTAMP, posted.processTimestamp());
        assertEquals(TransactionRecord.LENGTH, posted.toRecord().length());

        // ACCTFILE: balance moves, and a positive amount lands in the cycle *credit* field.
        AccountRecord account = accounts.read(ACCOUNT).orElseThrow();
        assertEquals(new BigDecimal("100.00"), account.currentBalance());
        assertEquals(new BigDecimal("100.00"), account.currentCycleCredit());
        assertEquals(new BigDecimal("0.00"), account.currentCycleDebit());

        // TCATBALF: the existing bucket is topped up rather than replaced.
        assertEquals(new BigDecimal("140.00"),
                balances.read(CategoryBalanceFile.key(ACCOUNT, "01", "0001")).orElseThrow().balance());

        // DALYREJS: nothing.
        assertTrue(job.rejectedTransactions().isEmpty());
    }

    @Test
    @DisplayName("R17: a negative amount is a debit, and zero counts as a credit")
    void splitsCycleTotalsBySign() {
        AccountMaster accounts = new AccountMaster(List.of(account()));
        PostingJob job = job(accounts, new CategoryBalanceFile(List.of()), PostingOptions.corrected());

        job.run(List.of(
                dailyTransaction("0000000000000001", CARD, "-25.00", "2024-06-01-12.00.00.000000"),
                dailyTransaction("0000000000000002", CARD, "0.00", "2024-06-01-12.00.00.000000")));

        AccountRecord account = accounts.read(ACCOUNT).orElseThrow();
        assertEquals(new BigDecimal("-25.00"), account.currentBalance());
        assertEquals(new BigDecimal("0.00"), account.currentCycleCredit());
        assertEquals(new BigDecimal("-25.00"), account.currentCycleDebit());
    }

    @Test
    @DisplayName("R14: a category bucket that does not exist yet is created at zero and announced")
    void createsMissingCategoryBucket() {
        AccountMaster accounts = new AccountMaster(List.of(account()));
        CategoryBalanceFile balances = new CategoryBalanceFile(List.of());
        PostingJob job = job(accounts, balances, PostingOptions.corrected());

        job.run(List.of(dailyTransaction("0000000000000001", CARD, "75.50",
                "2024-06-01-12.00.00.000000", "03", "0001")));

        TranCatBalRecord bucket = balances.read(CategoryBalanceFile.key(ACCOUNT, "03", "0001")).orElseThrow();
        assertEquals(new BigDecimal("75.50"), bucket.balance());
        assertEquals(TranCatBalRecord.LENGTH, bucket.toRecord().length());
        assertEquals(List.of("TCATBAL record not found for key : 1111111111103" + "0001.. Creating."),
                job.displayedMessages());
    }

    @Test
    @DisplayName("0100: the card is not in the cross reference file")
    void rejectsUnknownCard() {
        assertRejects(dailyTransaction("0000000000000001", "9999999999999999", "10.00",
                "2024-06-01-12.00.00.000000"), RejectReason.INVALID_CARD_NUMBER);
    }

    @Test
    @DisplayName("0101: the cross reference points at an account the master does not have")
    void rejectsUnknownAccount() {
        AccountMaster accounts = new AccountMaster(List.of());
        PostingJob job = new PostingJob(PostingOptions.corrected(), List.of(xref()), accounts,
                new CategoryBalanceFile(List.of()), () -> TIMESTAMP);

        PostingResult result = job.run(List.of(dailyTransaction("10.00")));

        assertEquals(RejectReason.ACCOUNT_NOT_FOUND, job.rejectedTransactions().get(0).reason());
        assertEquals(4, result.returnCode());
    }

    @Test
    @DisplayName("0102: the transaction takes the account past its credit limit")
    void rejectsOverLimit() {
        assertRejects(dailyTransaction("1000.01"), RejectReason.OVERLIMIT);
    }

    @Test
    @DisplayName("0103: the transaction is dated after the account expired")
    void rejectsAfterExpiration() {
        AccountMaster accounts = new AccountMaster(List.of(
                account(ACCOUNT, "1000.00", "0.00", "0.00", "0.00", "2024-05-31")));
        PostingJob job = job(accounts, new CategoryBalanceFile(List.of()), PostingOptions.corrected());

        job.run(List.of(dailyTransaction("10.00")));

        assertEquals(RejectReason.AFTER_EXPIRATION, job.rejectedTransactions().get(0).reason());
    }

    @Test
    @DisplayName("R21: a rejected transaction moves no money at all")
    void rejectedTransactionsLeaveEveryOtherFileAlone() {
        AccountMaster accounts = new AccountMaster(List.of(account()));
        CategoryBalanceFile balances = new CategoryBalanceFile(List.of(
                categoryBalance(ACCOUNT, "01", "0001", "40.00")));
        PostingJob job = job(accounts, balances, PostingOptions.corrected());

        PostingResult result = job.run(List.of(dailyTransaction("5000.00")));

        assertEquals(1, result.transactionsRejected());
        assertTrue(job.postedTransactions().isEmpty());
        assertEquals(new BigDecimal("0.00"), accounts.read(ACCOUNT).orElseThrow().currentBalance());
        assertEquals(new BigDecimal("40.00"),
                balances.read(CategoryBalanceFile.key(ACCOUNT, "01", "0001")).orElseThrow().balance());
    }

    @Test
    @DisplayName("the reject record is the 350 byte input verbatim plus an 80 byte trailer")
    void rejectRecordLayout() {
        DailyTransactionRecord transaction = dailyTransaction("5000.00");
        String record = new RejectRecord(transaction.raw(), RejectReason.OVERLIMIT).toRecord();

        assertEquals(430, record.length());
        assertEquals(transaction.raw(), record.substring(0, 350));
        assertEquals("0102", record.substring(350, 354));
        assertEquals("OVERLIMIT TRANSACTION", record.substring(354).strip());
        assertEquals(76, record.substring(354).length());
    }

    @Test
    @DisplayName("R2 and R23: the counter counts records read, and RC is 4 as soon as one is rejected")
    void countersAndReturnCode() {
        AccountMaster accounts = new AccountMaster(List.of(account()));
        PostingJob job = job(accounts, new CategoryBalanceFile(List.of()), PostingOptions.corrected());

        PostingResult result = job.run(List.of(
                dailyTransaction("0000000000000001", CARD, "10.00", "2024-06-01-12.00.00.000000"),
                dailyTransaction("0000000000000002", CARD, "9999.00", "2024-06-01-12.00.00.000000"),
                dailyTransaction("0000000000000003", CARD, "10.00", "2024-06-01-12.00.00.000000")));

        assertEquals(3, result.transactionsProcessed());
        assertEquals(1, result.transactionsRejected());
        assertEquals(2, result.transactionsPosted());
        assertEquals(4, result.returnCode());
        assertEquals(1L, result.rejectsByReason().get(RejectReason.OVERLIMIT));
    }

    @Test
    void returnCodeIsZeroWhenNothingIsRejected() {
        AccountMaster accounts = new AccountMaster(List.of(account()));
        PostingJob job = job(accounts, new CategoryBalanceFile(List.of()), PostingOptions.corrected());

        assertEquals(0, job.run(List.of(dailyTransaction("10.00"))).returnCode());
    }

    private void assertRejects(DailyTransactionRecord transaction, RejectReason expected) {
        AccountMaster accounts = new AccountMaster(List.of(account()));
        PostingJob job = job(accounts, new CategoryBalanceFile(List.of()), PostingOptions.corrected());

        PostingResult result = job.run(List.of(transaction));

        assertEquals(1, result.transactionsRejected());
        assertEquals(expected, job.rejectedTransactions().get(0).reason());
        assertEquals(4, result.returnCode());
    }

    static PostingJob job(AccountMaster accounts, CategoryBalanceFile balances, PostingOptions options) {
        List<CardXrefRecord> xrefs = List.of(xref());
        return new PostingJob(options, xrefs, accounts, balances, () -> TIMESTAMP);
    }
}
