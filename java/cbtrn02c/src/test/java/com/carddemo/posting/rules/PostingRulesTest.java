package com.carddemo.posting.rules;

import com.carddemo.interest.records.AccountRecord;
import com.carddemo.posting.PostingOptions;
import com.carddemo.posting.records.DailyTransactionRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.carddemo.posting.Fixtures.account;
import static com.carddemo.posting.Fixtures.dailyTransaction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each rule on its own, at the limit and one unit either side. One unit is 0.01 for money
 * (PIC ...V99) and one day for the expiry date (PIC X(10), YYYY-MM-DD).
 */
class PostingRulesTest {

    private final PostingRules corrected = new PostingRules(PostingOptions.corrected());
    private final PostingRules asCobol = new PostingRules(PostingOptions.bugForBug());

    @Nested
    @DisplayName("R8 / 0102 - credit limit")
    class CreditLimit {

        private final AccountRecord account = account(1L, "1000.00", "0.00", "0.00", "0.00", "2099-12-31");

        @Test
        void oneCentBelowTheLimitIsAccepted() {
            assertTrue(corrected.withinCreditLimit(account, new BigDecimal("999.99")));
        }

        @Test
        void exactlyAtTheLimitIsAccepted() {
            // ACCT-CREDIT-LIMIT >= WS-TEMP-BAL, so equality passes.
            assertTrue(corrected.withinCreditLimit(account, new BigDecimal("1000.00")));
        }

        @Test
        void oneCentOverTheLimitIsRejected() {
            assertFalse(corrected.withinCreditLimit(account, new BigDecimal("1000.01")));
        }

        @Test
        void cycleActivityAlreadyUsedCountsAgainstTheLimit() {
            AccountRecord used = account(1L, "1000.00", "0.00", "400.00", "0.00", "2099-12-31");
            assertTrue(corrected.withinCreditLimit(used, new BigDecimal("600.00")));
            assertFalse(corrected.withinCreditLimit(used, new BigDecimal("600.01")));
        }

        @Test
        @DisplayName("D3 - ACCT-CURR-BAL is not in the COBOL formula")
        void currentBalanceIsIgnoredUnlessAskedFor() {
            AccountRecord owing = account(1L, "1000.00", "5000.00", "0.00", "0.00", "2099-12-31");
            assertTrue(corrected.withinCreditLimit(owing, new BigDecimal("10.00")));

            PostingRules strict = new PostingRules(
                    PostingOptions.corrected().withIncludeCurrentBalanceInCreditLimitCheck(true));
            assertFalse(strict.withinCreditLimit(owing, new BigDecimal("10.00")));
        }

        @Test
        @DisplayName("D4 - a refund either frees the limit up or uses it up")
        void refundsMoveTheLimitInOppositeDirections() {
            AccountRecord refunded = account(1L, "1000.00", "0.00", "900.00", "-500.00", "2099-12-31");

            // COBOL: 900 - (-500) = 1400, already over the limit before this transaction.
            assertEquals(new BigDecimal("1400.00"), asCobol.availableLimitFigure(refunded, BigDecimal.ZERO));
            assertFalse(asCobol.withinCreditLimit(refunded, new BigDecimal("0.00")));

            // Corrected: 900 + (-500) = 400 used, so 600 of headroom is left.
            assertEquals(new BigDecimal("400.00"), corrected.availableLimitFigure(refunded, BigDecimal.ZERO));
            assertTrue(corrected.withinCreditLimit(refunded, new BigDecimal("600.00")));
            assertFalse(corrected.withinCreditLimit(refunded, new BigDecimal("600.01")));
        }

        @Test
        @DisplayName("D5 - WS-TEMP-BAL is PIC S9(09)V99, one digit short of its inputs")
        void tempBalanceWrapsAtOneBillionInCobolButNotWhenCorrected() {
            AccountRecord large = account(1L, "9999999999.00", "0.00", "1000000000.00", "0.00", "2099-12-31");

            assertEquals(new BigDecimal("0.00"), asCobol.availableLimitFigure(large, BigDecimal.ZERO));
            assertEquals(new BigDecimal("1000000000.00"), corrected.availableLimitFigure(large, BigDecimal.ZERO));
        }

        @Test
        void truncationKeepsTheSign() {
            assertEquals(new BigDecimal("-0.01"),
                    PostingRules.truncateToPicS9v99(new BigDecimal("-1000000000.01")));
        }
    }

    @Nested
    @DisplayName("R9 / 0103 - account expiry")
    class Expiry {

        private final AccountRecord account = account(1L, "1000.00", "0.00", "0.00", "0.00", "2024-06-15");

        @Test
        void theDayBeforeExpiryIsAccepted() {
            assertTrue(corrected.notAfterExpiration(account, at("2024-06-14")));
        }

        @Test
        void exactlyOnTheExpiryDateIsAccepted() {
            // ACCT-EXPIRAION-DATE >= DALYTRAN-ORIG-TS (1:10), so equality passes.
            assertTrue(corrected.notAfterExpiration(account, at("2024-06-15")));
        }

        @Test
        void theDayAfterExpiryIsRejected() {
            assertFalse(corrected.notAfterExpiration(account, at("2024-06-16")));
        }

        @Test
        @DisplayName("the comparison is textual, which is only safe because both are YYYY-MM-DD")
        void comparesTheFirstTenCharactersAsText() {
            assertEquals("2024-06-16", at("2024-06-16").originDate());
            assertFalse(corrected.notAfterExpiration(account, at("2024-12-01")));
            assertTrue(corrected.notAfterExpiration(account, at("2023-12-31")));
        }

        private DailyTransactionRecord at(String date) {
            return dailyTransaction("0000000000000001", com.carddemo.posting.Fixtures.CARD, "1.00",
                    date + "-12.00.00.000000");
        }
    }
}
