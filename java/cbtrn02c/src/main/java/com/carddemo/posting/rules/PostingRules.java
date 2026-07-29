package com.carddemo.posting.rules;

import com.carddemo.interest.records.AccountRecord;
import com.carddemo.posting.PostingOptions;
import com.carddemo.posting.records.DailyTransactionRecord;

import java.math.BigDecimal;

/**
 * The validation ruleset of {@code 1500-VALIDATE-TRAN}, one rule per method, each stating
 * the rule in business terms, the COBOL paragraph it comes from and the reject code it
 * produces. Every method is a pure function of its arguments so it can be tested on its own.
 *
 * <p>Rule numbers (R6, R8, ...) refer to the catalogue in {@code CBTRN02C-EXPLAINED.md}.
 */
public final class PostingRules {

    /** PIC S9(09)V99 holds at most 999,999,999.99 - see finding D5. */
    static final BigDecimal TEMP_BALANCE_MODULUS = new BigDecimal("1000000000.00");

    private final PostingOptions options;

    public PostingRules(PostingOptions options) {
        this.options = options;
    }

    /**
     * R6 / reject 0100 - the card number on the transaction must exist in the card cross
     * reference file, because that is the only thing that says whose account it is.
     * COBOL: {@code 1500-A-LOOKUP-XREF}, CBTRN02C.cbl:380-392.
     */
    public boolean cardIsKnown(boolean cardFoundInXref) {
        return cardFoundInXref;
    }

    /**
     * R7 / reject 0101 - the account the cross reference points at must exist in the account
     * master. Failing this means the two files disagree, not that the customer did anything
     * wrong. COBOL: {@code 1500-B-LOOKUP-ACCT}, CBTRN02C.cbl:393-399.
     */
    public boolean accountIsKnown(boolean accountFoundInMaster) {
        return accountFoundInMaster;
    }

    /**
     * R8 / reject 0102 - the transaction must not take the account past its credit limit.
     * COBOL: CBTRN02C.cbl:403-413.
     *
     * <pre>
     *   WS-TEMP-BAL = ACCT-CURR-CYC-CREDIT - ACCT-CURR-CYC-DEBIT + DALYTRAN-AMT
     *   reject unless ACCT-CREDIT-LIMIT &gt;= WS-TEMP-BAL
     * </pre>
     *
     * Note what is <em>not</em> in that formula: {@code ACCT-CURR-BAL}, the money the
     * customer actually owes. Findings D3 and D4 cover the two arguable parts; both are
     * controlled by {@link PostingOptions} so either behaviour can be selected and tested.
     */
    public boolean withinCreditLimit(AccountRecord account, BigDecimal amount) {
        return account.creditLimit().compareTo(availableLimitFigure(account, amount)) >= 0;
    }

    /** The value the COBOL calls WS-TEMP-BAL. Exposed so tests can assert on it directly. */
    public BigDecimal availableLimitFigure(AccountRecord account, BigDecimal amount) {
        BigDecimal cycleActivity = options.refundsCountAgainstLimit()
                // COBOL as written: subtracting a negative refund *increases* the figure (D4).
                ? account.currentCycleCredit().subtract(account.currentCycleDebit())
                // Corrected: the cycle's net movement, so a refund frees the limit back up.
                : account.currentCycleCredit().add(account.currentCycleDebit());

        BigDecimal figure = cycleActivity.add(amount);
        if (options.includeCurrentBalanceInCreditLimitCheck()) {
            figure = figure.add(account.currentBalance());
        }
        return options.truncateTempBalance() ? truncateToPicS9v99(figure) : figure;
    }

    /**
     * D5 - WS-TEMP-BAL is PIC S9(09)V99 while the fields feeding it are PIC S9(10)V99, and
     * the COMPUTE has no ON SIZE ERROR, so high order digits are silently dropped.
     */
    static BigDecimal truncateToPicS9v99(BigDecimal value) {
        BigDecimal remainder = value.abs().remainder(TEMP_BALANCE_MODULUS);
        return value.signum() < 0 ? remainder.negate() : remainder;
    }

    /**
     * R9 / reject 0103 - the transaction must not be dated after the account expired.
     * COBOL: CBTRN02C.cbl:414-420, {@code ACCT-EXPIRAION-DATE >= DALYTRAN-ORIG-TS (1:10)}.
     *
     * <p>Both operands are PIC X, so this is a <em>character</em> comparison that happens to
     * be correct because both are {@code YYYY-MM-DD}. It is inclusive: a transaction dated
     * exactly on the expiry date is accepted.
     */
    public boolean notAfterExpiration(AccountRecord account, DailyTransactionRecord transaction) {
        return account.expirationDate().compareTo(transaction.originDate()) >= 0;
    }
}
