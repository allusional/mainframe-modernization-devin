package com.carddemo.posting;

/**
 * Which behaviour to use where the COBOL does something this port believes is wrong.
 *
 * <p>Each flag defaults to the corrected behaviour. Turning them all on
 * ({@link #bugForBug()}) reproduces CBTRN02C exactly, which is what the differential test
 * harness in {@code scripts/cobol-parity} runs. Findings D1..D8 are described in
 * {@code CBTRN02C-EXPLAINED.md}.
 *
 * @param lostAccountUpdateIsSilent          D1. COBOL sets reason 109 when the account rewrite fails and
 *                                           then never looks at it, so the transaction is still written to the
 *                                           master, the category balance still moves, and nothing is reported.
 *                                           Corrected: reject the transaction with 0109, roll the category
 *                                           balance back, count it and set RC=4.
 * @param abendOnDuplicateTransactionId      D2. COBOL abends on file status 22. Corrected: reject with 0110.
 * @param includeCurrentBalanceInCreditLimitCheck
 *                                           D3. <strong>Not</strong> a correction: the COBOL formula ignores
 *                                           ACCT-CURR-BAL and this port cannot tell whether that is deliberate,
 *                                           so it defaults to the COBOL behaviour (false) and this flag opts in
 *                                           to the stricter check.
 * @param refundsCountAgainstLimit           D4. COBOL computes {@code CYC-CREDIT - CYC-DEBIT}; because refunds
 *                                           are stored as negatives in CYC-DEBIT, that makes a refund push the
 *                                           customer <em>closer</em> to their limit. Corrected: add them.
 * @param truncateTempBalance                D5. Emulate WS-TEMP-BAL being PIC S9(09)V99 with no ON SIZE ERROR.
 * @param lastRejectReasonWins               D8. COBOL lets the expiry check overwrite an over-limit code, so
 *                                           only 0103 is ever reported when both apply. Corrected: report the
 *                                           first rule that failed.
 */
public record PostingOptions(boolean lostAccountUpdateIsSilent,
                             boolean abendOnDuplicateTransactionId,
                             boolean includeCurrentBalanceInCreditLimitCheck,
                             boolean refundsCountAgainstLimit,
                             boolean truncateTempBalance,
                             boolean lastRejectReasonWins) {

    /** Every defect corrected; the default. */
    public static PostingOptions corrected() {
        return new PostingOptions(false, false, false, false, false, false);
    }

    /** Every documented CBTRN02C behaviour reproduced exactly, defects included. */
    public static PostingOptions bugForBug() {
        return new PostingOptions(true, true, false, true, true, true);
    }

    public PostingOptions withLostAccountUpdateIsSilent(boolean value) {
        return new PostingOptions(value, abendOnDuplicateTransactionId, includeCurrentBalanceInCreditLimitCheck,
                refundsCountAgainstLimit, truncateTempBalance, lastRejectReasonWins);
    }

    public PostingOptions withAbendOnDuplicateTransactionId(boolean value) {
        return new PostingOptions(lostAccountUpdateIsSilent, value, includeCurrentBalanceInCreditLimitCheck,
                refundsCountAgainstLimit, truncateTempBalance, lastRejectReasonWins);
    }

    public PostingOptions withIncludeCurrentBalanceInCreditLimitCheck(boolean value) {
        return new PostingOptions(lostAccountUpdateIsSilent, abendOnDuplicateTransactionId, value,
                refundsCountAgainstLimit, truncateTempBalance, lastRejectReasonWins);
    }

    public PostingOptions withRefundsCountAgainstLimit(boolean value) {
        return new PostingOptions(lostAccountUpdateIsSilent, abendOnDuplicateTransactionId,
                includeCurrentBalanceInCreditLimitCheck, value, truncateTempBalance, lastRejectReasonWins);
    }

    public PostingOptions withTruncateTempBalance(boolean value) {
        return new PostingOptions(lostAccountUpdateIsSilent, abendOnDuplicateTransactionId,
                includeCurrentBalanceInCreditLimitCheck, refundsCountAgainstLimit, value, lastRejectReasonWins);
    }

    public PostingOptions withLastRejectReasonWins(boolean value) {
        return new PostingOptions(lostAccountUpdateIsSilent, abendOnDuplicateTransactionId,
                includeCurrentBalanceInCreditLimitCheck, refundsCountAgainstLimit, truncateTempBalance, value);
    }
}
