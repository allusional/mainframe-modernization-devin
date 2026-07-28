package com.carddemo.cbtrn02c.service;

import com.carddemo.cbtrn02c.TestRecords;
import com.carddemo.cbtrn02c.model.AccountRecord;
import com.carddemo.cbtrn02c.model.CardXrefRecord;
import com.carddemo.cbtrn02c.model.DailyTransactionRecord;
import com.carddemo.cbtrn02c.model.TranCatBalRecord;
import com.carddemo.cbtrn02c.repository.AccountRepository;
import com.carddemo.cbtrn02c.repository.CardXrefRepository;
import com.carddemo.cbtrn02c.repository.TranCatBalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionPostingServiceTest {

    private static final String CARD = "4859452612877065";
    private static final long ACCOUNT_ID = 55L;
    private static final String TYPE = "01";
    private static final String CATEGORY = "0001";
    private static final ProcessingTimestampProvider FIXED_TS = () -> "2022-06-10-19.27.53.000000";

    private CardXrefRepository xref;
    private AccountRepository accounts;
    private TranCatBalRepository tranCatBal;
    private TransactionPostingService service;

    @BeforeEach
    void setUp() {
        xref = new CardXrefRepository();
        accounts = new AccountRepository();
        tranCatBal = new TranCatBalRepository();
        service = new TransactionPostingService(xref, accounts, tranCatBal, FIXED_TS);
    }

    private void givenXref() {
        xref.put(new CardXrefRecord(CARD, 999L, ACCOUNT_ID));
    }

    private AccountRecord givenAccount(BigDecimal balance, BigDecimal creditLimit,
                                       String expiration, BigDecimal cycCredit, BigDecimal cycDebit) {
        AccountRecord account = AccountRecord.parse(
                TestRecords.account(ACCOUNT_ID, balance, creditLimit, expiration, cycCredit, cycDebit));
        accounts.put(account);
        return account;
    }

    private DailyTransactionRecord daly(BigDecimal amount, String cardNumber, String origTs) {
        return DailyTransactionRecord.parse(
                TestRecords.dailyTran("TXN0000000000001", TYPE, CATEGORY, amount, cardNumber, origTs));
    }

    @Test
    void postsValidPurchaseAndUpdatesAccountAndCategoryBalance() {
        givenXref();
        AccountRecord account = givenAccount(new BigDecimal("100.00"), new BigDecimal("5000.00"),
                "2025-12-31", new BigDecimal("200.00"), new BigDecimal("50.00"));

        PostingOutcome outcome = service.process(daly(new BigDecimal("40.00"), CARD, "2022-06-10 19:27:53.000000"));

        assertTrue(outcome.isPosted());
        assertEquals("2022-06-10-19.27.53.000000", outcome.getPostedTransaction().getProcessingTimestamp());
        // ACCT-CURR-BAL += amount
        assertEquals(new BigDecimal("140.00"), account.getCurrentBalance());
        // positive amount -> ACCT-CURR-CYC-CREDIT += amount, debit unchanged
        assertEquals(new BigDecimal("240.00"), account.getCurrentCycleCredit());
        assertEquals(new BigDecimal("50.00"), account.getCurrentCycleDebit());
        // new category balance record created with the transaction amount
        assertEquals(new BigDecimal("40.00"),
                tranCatBal.findByKey(TranCatBalRecord.key(ACCOUNT_ID, TYPE, CATEGORY)).orElseThrow().getBalance());
    }

    @Test
    void negativeAmountUpdatesCycleDebitNotCredit() {
        givenXref();
        AccountRecord account = givenAccount(new BigDecimal("100.00"), new BigDecimal("5000.00"),
                "2025-12-31", new BigDecimal("200.00"), new BigDecimal("50.00"));

        PostingOutcome outcome = service.process(daly(new BigDecimal("-30.00"), CARD, "2022-06-10 19:27:53.000000"));

        assertTrue(outcome.isPosted());
        assertEquals(new BigDecimal("70.00"), account.getCurrentBalance());
        assertEquals(new BigDecimal("200.00"), account.getCurrentCycleCredit());
        assertEquals(new BigDecimal("20.00"), account.getCurrentCycleDebit());
    }

    @Test
    void addsToExistingCategoryBalance() {
        givenXref();
        givenAccount(new BigDecimal("100.00"), new BigDecimal("5000.00"),
                "2025-12-31", new BigDecimal("0.00"), new BigDecimal("0.00"));
        tranCatBal.put(TranCatBalRecord.parse(
                TestRecords.tranCatBal(ACCOUNT_ID, TYPE, CATEGORY, new BigDecimal("15.50"))));

        service.process(daly(new BigDecimal("4.50"), CARD, "2022-06-10 19:27:53.000000"));

        assertEquals(new BigDecimal("20.00"),
                tranCatBal.findByKey(TranCatBalRecord.key(ACCOUNT_ID, TYPE, CATEGORY)).orElseThrow().getBalance());
    }

    @Test
    void rejectsUnknownCardWithReason100() {
        // No xref set up
        givenAccount(new BigDecimal("0.00"), new BigDecimal("5000.00"),
                "2025-12-31", new BigDecimal("0.00"), new BigDecimal("0.00"));

        PostingOutcome outcome = service.process(daly(new BigDecimal("10.00"), CARD, "2022-06-10 19:27:53.000000"));

        assertTrue(outcome.isRejected());
        assertEquals(ValidationResult.INVALID_CARD_NUMBER, outcome.getValidation().getReasonCode());
        assertEquals("INVALID CARD NUMBER FOUND", outcome.getValidation().getReasonDescription());
    }

    @Test
    void rejectsMissingAccountWithReason101() {
        xref.put(new CardXrefRecord(CARD, 999L, 88L)); // xref points to an account that does not exist

        PostingOutcome outcome = service.process(daly(new BigDecimal("10.00"), CARD, "2022-06-10 19:27:53.000000"));

        assertTrue(outcome.isRejected());
        assertEquals(ValidationResult.ACCOUNT_NOT_FOUND, outcome.getValidation().getReasonCode());
    }

    @Test
    void rejectsOverlimitWithReason102() {
        givenXref();
        // creditLimit 100; tempBal = cycCredit(90) - cycDebit(0) + amt(20) = 110 > 100
        givenAccount(new BigDecimal("0.00"), new BigDecimal("100.00"),
                "2025-12-31", new BigDecimal("90.00"), new BigDecimal("0.00"));

        PostingOutcome outcome = service.process(daly(new BigDecimal("20.00"), CARD, "2022-06-10 19:27:53.000000"));

        assertTrue(outcome.isRejected());
        assertEquals(ValidationResult.OVERLIMIT, outcome.getValidation().getReasonCode());
    }

    @Test
    void allowsTransactionExactlyAtCreditLimit() {
        givenXref();
        // tempBal = 80 + 20 = 100 == creditLimit -> ACCT-CREDIT-LIMIT >= WS-TEMP-BAL is true
        givenAccount(new BigDecimal("0.00"), new BigDecimal("100.00"),
                "2025-12-31", new BigDecimal("80.00"), new BigDecimal("0.00"));

        PostingOutcome outcome = service.process(daly(new BigDecimal("20.00"), CARD, "2022-06-10 19:27:53.000000"));

        assertTrue(outcome.isPosted());
    }

    @Test
    void rejectsExpiredAccountWithReason103() {
        givenXref();
        // expiration 2022-06-09 < orig date 2022-06-10
        givenAccount(new BigDecimal("0.00"), new BigDecimal("5000.00"),
                "2022-06-09", new BigDecimal("0.00"), new BigDecimal("0.00"));

        PostingOutcome outcome = service.process(daly(new BigDecimal("10.00"), CARD, "2022-06-10 19:27:53.000000"));

        assertTrue(outcome.isRejected());
        assertEquals(ValidationResult.AFTER_EXPIRATION, outcome.getValidation().getReasonCode());
    }

    @Test
    void expirationReasonWinsWhenBothOverlimitAndExpired() {
        givenXref();
        // both checks fail; COBOL evaluates overlimit then expiration, so 103 survives
        givenAccount(new BigDecimal("0.00"), new BigDecimal("100.00"),
                "2022-06-09", new BigDecimal("90.00"), new BigDecimal("0.00"));

        PostingOutcome outcome = service.process(daly(new BigDecimal("20.00"), CARD, "2022-06-10 19:27:53.000000"));

        assertEquals(ValidationResult.AFTER_EXPIRATION, outcome.getValidation().getReasonCode());
    }

    @Test
    void allowsTransactionOnExpirationDay() {
        givenXref();
        // expiration == orig date -> ACCT-EXPIRAION-DATE >= DALYTRAN-ORIG-TS(1:10) true
        givenAccount(new BigDecimal("0.00"), new BigDecimal("5000.00"),
                "2022-06-10", new BigDecimal("0.00"), new BigDecimal("0.00"));

        PostingOutcome outcome = service.process(daly(new BigDecimal("10.00"), CARD, "2022-06-10 19:27:53.000000"));

        assertTrue(outcome.isPosted());
    }

    @Test
    void rejectRecordHasRawDataPlus80ByteTrailer() {
        givenAccount(new BigDecimal("0.00"), new BigDecimal("5000.00"),
                "2025-12-31", new BigDecimal("0.00"), new BigDecimal("0.00"));
        DailyTransactionRecord d = daly(new BigDecimal("10.00"), CARD, "2022-06-10 19:27:53.000000");

        PostingOutcome outcome = service.process(d);
        String reject = outcome.toRejectRecord();

        assertEquals(430, reject.length());
        assertEquals(d.getRawRecord(), reject.substring(0, 350));
        assertEquals("0100", reject.substring(350, 354));
        assertTrue(reject.substring(354).startsWith("INVALID CARD NUMBER FOUND"));
        assertFalse(outcome.isPosted());
    }
}
