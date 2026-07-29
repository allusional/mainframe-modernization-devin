package com.carddemo.posting;

import com.carddemo.interest.records.AccountRecord;
import com.carddemo.posting.files.AccountMaster;
import com.carddemo.posting.files.CategoryBalanceFile;
import com.carddemo.posting.rules.RejectReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.carddemo.posting.Fixtures.ACCOUNT;
import static com.carddemo.posting.Fixtures.CARD;
import static com.carddemo.posting.Fixtures.account;
import static com.carddemo.posting.Fixtures.categoryBalance;
import static com.carddemo.posting.Fixtures.dailyTransaction;
import static com.carddemo.posting.PostingJobTest.job;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Both sides of every behaviour Phase 1 flagged: what the COBOL does, and what this port
 * does instead by default. Findings D1..D8 are described in CBTRN02C-EXPLAINED.md.
 */
class SuspectedDefectTest {

    @Nested
    @DisplayName("D1 - the account rewrite fails and the COBOL never notices")
    class LostAccountUpdate {

        /** An ACCTFILE whose REWRITE always comes back INVALID KEY. */
        private AccountMaster failing() {
            return new AccountMaster(List.of(account())) {
                @Override
                public boolean rewrite(AccountRecord account) {
                    return false;
                }
            };
        }

        @Test
        @DisplayName("emulated: the transaction posts and the category balance moves anyway")
        void cobolPostsTheTransactionRegardless() {
            CategoryBalanceFile balances = new CategoryBalanceFile(List.of(
                    categoryBalance(ACCOUNT, "01", "0001", "0.00")));
            PostingJob job = job(failing(), balances, PostingOptions.bugForBug());

            PostingResult result = job.run(List.of(dailyTransaction("100.00")));

            assertEquals(0, result.transactionsRejected());
            assertEquals(1, job.postedTransactions().size());
            assertEquals(new BigDecimal("100.00"),
                    balances.read(CategoryBalanceFile.key(ACCOUNT, "01", "0001")).orElseThrow().balance());
            assertEquals(0, result.returnCode());
        }

        @Test
        @DisplayName("corrected: reject 0109, touch nothing else, and set RC=4")
        void correctedRejectsAndLeavesNothingBehind() {
            CategoryBalanceFile balances = new CategoryBalanceFile(List.of(
                    categoryBalance(ACCOUNT, "01", "0001", "0.00")));
            PostingJob job = job(failing(), balances, PostingOptions.corrected());

            PostingResult result = job.run(List.of(dailyTransaction("100.00")));

            assertEquals(1, result.transactionsRejected());
            assertEquals(RejectReason.ACCOUNT_REWRITE_FAILED, job.rejectedTransactions().get(0).reason());
            assertEquals("0109", job.rejectedTransactions().get(0).toRecord().substring(350, 354));
            assertTrue(job.postedTransactions().isEmpty());
            assertEquals(new BigDecimal("0.00"),
                    balances.read(CategoryBalanceFile.key(ACCOUNT, "01", "0001")).orElseThrow().balance());
            assertEquals(4, result.returnCode());
        }
    }

    @Nested
    @DisplayName("D2 - a duplicate transaction id")
    class DuplicateTransactionId {

        @Test
        @DisplayName("emulated: the run abends and the rest of the feed is never seen")
        void cobolAbends() {
            PostingJob job = job(new AccountMaster(List.of(account())), new CategoryBalanceFile(List.of()),
                    PostingOptions.bugForBug());

            AbendException abend = assertThrows(AbendException.class, () -> job.run(List.of(
                    dailyTransaction("0000000000000001", CARD, "10.00", "2024-06-01-12.00.00.000000"),
                    dailyTransaction("0000000000000001", CARD, "20.00", "2024-06-01-12.00.00.000000"),
                    dailyTransaction("0000000000000002", CARD, "30.00", "2024-06-01-12.00.00.000000"))));

            assertTrue(abend.getMessage().contains("0022"));
            assertEquals(1, job.postedTransactions().size());
        }

        @Test
        @DisplayName("corrected: reject 0110 and carry on to the end of the feed")
        void correctedRejectsAndContinues() {
            PostingJob job = job(new AccountMaster(List.of(account())), new CategoryBalanceFile(List.of()),
                    PostingOptions.corrected());

            PostingResult result = job.run(List.of(
                    dailyTransaction("0000000000000001", CARD, "10.00", "2024-06-01-12.00.00.000000"),
                    dailyTransaction("0000000000000001", CARD, "20.00", "2024-06-01-12.00.00.000000"),
                    dailyTransaction("0000000000000002", CARD, "30.00", "2024-06-01-12.00.00.000000")));

            assertEquals(3, result.transactionsProcessed());
            assertEquals(1, result.transactionsRejected());
            assertEquals(RejectReason.DUPLICATE_TRANSACTION_ID, job.rejectedTransactions().get(0).reason());
            assertEquals(2, job.postedTransactions().size());
        }
    }

    @Nested
    @DisplayName("D4 - a refund earlier in the cycle")
    class RefundsAgainstTheLimit {

        /** 900.00 charged and 500.00 refunded this cycle, against a 1000.00 limit. */
        private AccountMaster refunded() {
            return new AccountMaster(List.of(
                    account(ACCOUNT, "1000.00", "400.00", "900.00", "-500.00", "2099-12-31")));
        }

        @Test
        @DisplayName("emulated: a 100.00 purchase is declined, because the refund counted as spend")
        void cobolDeclines() {
            PostingJob job = job(refunded(), new CategoryBalanceFile(List.of()), PostingOptions.bugForBug());

            PostingResult result = job.run(List.of(dailyTransaction("100.00")));

            assertEquals(1, result.transactionsRejected());
            assertEquals(RejectReason.OVERLIMIT, job.rejectedTransactions().get(0).reason());
        }

        @Test
        @DisplayName("corrected: the same purchase is approved, with 600.00 of headroom left")
        void correctedApproves() {
            PostingJob job = job(refunded(), new CategoryBalanceFile(List.of()), PostingOptions.corrected());

            assertEquals(0, job.run(List.of(dailyTransaction("100.00"))).transactionsRejected());
        }
    }

    @Nested
    @DisplayName("D8 - both the credit limit and the expiry check fail on the same transaction")
    class LastReasonWins {

        private AccountMaster expiredAndMaxedOut() {
            return new AccountMaster(List.of(
                    account(ACCOUNT, "10.00", "0.00", "0.00", "0.00", "2020-01-01")));
        }

        @Test
        @DisplayName("emulated: only 0103 is reported, the over-limit code is overwritten")
        void cobolReportsTheLastFailure() {
            PostingJob job = job(expiredAndMaxedOut(), new CategoryBalanceFile(List.of()),
                    PostingOptions.bugForBug());

            job.run(List.of(dailyTransaction("500.00")));

            assertEquals(RejectReason.AFTER_EXPIRATION, job.rejectedTransactions().get(0).reason());
        }

        @Test
        @DisplayName("corrected: the first rule that failed is the one reported")
        void correctedReportsTheFirstFailure() {
            PostingJob job = job(expiredAndMaxedOut(), new CategoryBalanceFile(List.of()),
                    PostingOptions.corrected());

            job.run(List.of(dailyTransaction("500.00")));

            assertEquals(RejectReason.OVERLIMIT, job.rejectedTransactions().get(0).reason());
        }
    }
}
