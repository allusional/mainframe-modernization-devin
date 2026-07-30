package com.carddemo.cbtrn02c;

import com.carddemo.cbtrn02c.copybook.DalyTranRecord;
import com.carddemo.cbtrn02c.copybook.RejectRecord;
import com.carddemo.cbtrn02c.copybook.TranRecord;
import com.carddemo.cbtrn02c.testsupport.BatchFixture;
import com.carddemo.cbtrn02c.testsupport.RecordImages;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for the ported business logic (validation 1500 and posting 2000). */
class PostTranBatchTest {

    private static final String CARD = "4859452612877065";
    private static final String ACCOUNT = "00000000001";
    private static final String ORIG_TS = "2022-06-10 19:27:53.000000";

    private static DalyTranRecord tran(BigDecimal amount) {
        return tran("0000000000000001", "01", "0001", amount, CARD);
    }

    private static DalyTranRecord tran(String id, String typeCd, String catCd, BigDecimal amount, String card) {
        return DalyTranRecord.parse(RecordImages.dalyTran(id, typeCd, catCd, amount, card, ORIG_TS));
    }

    private static BatchFixture fixtureWithAccount(BigDecimal balance, BigDecimal creditLimit,
                                                   BigDecimal cycleCredit, BigDecimal cycleDebit,
                                                   String expirationDate) {
        return BatchFixture.empty()
                .withXref(RecordImages.cardXref(CARD, "000000001", ACCOUNT))
                .withAccount(RecordImages.account(ACCOUNT, balance, creditLimit, cycleCredit, cycleDebit,
                        expirationDate));
    }

    @Test
    void rejectsUnknownCardNumberWithReason100() {
        BatchFixture fixture = BatchFixture.empty();
        PostTranBatch batch = fixture.batch();

        PostTranBatch.Result result = batch.run(List.of(tran(new BigDecimal("10.00"))));

        assertEquals(new PostTranBatch.Result(1, 1, 4), result);
        RejectRecord reject = batch.rejectFile().get(0);
        assertEquals(PostTranBatch.REASON_INVALID_CARD, reject.failReason());
        assertEquals("INVALID CARD NUMBER FOUND", reject.failReasonDescription().strip());
        assertTrue(batch.transactFile().isEmpty());
    }

    @Test
    void rejectsMissingAccountWithReason101() {
        PostTranBatch batch = BatchFixture.empty()
                .withXref(RecordImages.cardXref(CARD, "000000001", ACCOUNT))
                .batch();

        batch.run(List.of(tran(new BigDecimal("10.00"))));

        RejectRecord reject = batch.rejectFile().get(0);
        assertEquals(PostTranBatch.REASON_ACCOUNT_NOT_FOUND, reject.failReason());
        assertEquals("ACCOUNT RECORD NOT FOUND", reject.failReasonDescription().strip());
    }

    @Test
    void rejectsOverlimitTransactionWithReason102() {
        // WS-TEMP-BAL = 900.00 - 100.00 + 300.00 = 1100.00 > credit limit 1000.00
        PostTranBatch batch = fixtureWithAccount(new BigDecimal("0.00"), new BigDecimal("1000.00"),
                new BigDecimal("900.00"), new BigDecimal("100.00"), "2025-05-20").batch();

        batch.run(List.of(tran(new BigDecimal("300.00"))));

        RejectRecord reject = batch.rejectFile().get(0);
        assertEquals(PostTranBatch.REASON_OVERLIMIT, reject.failReason());
        assertEquals("OVERLIMIT TRANSACTION", reject.failReasonDescription().strip());
    }

    @Test
    void acceptsTransactionExactlyAtTheCreditLimit() {
        // ACCT-CREDIT-LIMIT >= WS-TEMP-BAL is accepted
        PostTranBatch batch = fixtureWithAccount(new BigDecimal("0.00"), new BigDecimal("1000.00"),
                new BigDecimal("900.00"), new BigDecimal("100.00"), "2025-05-20").batch();

        PostTranBatch.Result result = batch.run(List.of(tran(new BigDecimal("200.00"))));

        assertEquals(new PostTranBatch.Result(1, 0, 0), result);
        assertEquals(1, batch.transactFile().size());
    }

    @Test
    void rejectsTransactionReceivedAfterAccountExpirationWithReason103() {
        PostTranBatch batch = fixtureWithAccount(new BigDecimal("0.00"), new BigDecimal("1000.00"),
                new BigDecimal("0.00"), new BigDecimal("0.00"), "2022-06-09").batch();

        batch.run(List.of(tran(new BigDecimal("10.00"))));

        RejectRecord reject = batch.rejectFile().get(0);
        assertEquals(PostTranBatch.REASON_EXPIRED, reject.failReason());
        assertEquals("TRANSACTION RECEIVED AFTER ACCT EXPIRATION", reject.failReasonDescription().strip());
    }

    @Test
    void acceptsTransactionOnTheExpirationDateItself() {
        // ACCT-EXPIRAION-DATE >= DALYTRAN-ORIG-TS(1:10) is accepted
        PostTranBatch batch = fixtureWithAccount(new BigDecimal("0.00"), new BigDecimal("1000.00"),
                new BigDecimal("0.00"), new BigDecimal("0.00"), "2022-06-10").batch();

        assertEquals(new PostTranBatch.Result(1, 0, 0), batch.run(List.of(tran(new BigDecimal("10.00")))));
    }

    @Test
    void expirationReasonOverwritesOverlimitWhenBothChecksFail() {
        // both 1500-B checks run; the expiration MOVE happens last, as in the COBOL
        PostTranBatch batch = fixtureWithAccount(new BigDecimal("0.00"), new BigDecimal("100.00"),
                new BigDecimal("0.00"), new BigDecimal("0.00"), "2022-06-09").batch();

        batch.run(List.of(tran(new BigDecimal("500.00"))));

        assertEquals(PostTranBatch.REASON_EXPIRED, batch.rejectFile().get(0).failReason());
    }

    @Test
    void postsCreditAndCreatesTheCategoryBalanceRecord() {
        BatchFixture fixture = fixtureWithAccount(new BigDecimal("50.00"), new BigDecimal("1000.00"),
                new BigDecimal("10.00"), new BigDecimal("5.00"), "2025-05-20");
        PostTranBatch batch = fixture.batch();

        batch.run(List.of(tran(new BigDecimal("100.25"))));

        assertEquals(new BigDecimal("150.25"), fixture.account(ACCOUNT).currentBalance());
        assertEquals(new BigDecimal("110.25"), fixture.account(ACCOUNT).currentCycleCredit());
        assertEquals(new BigDecimal("5.00"), fixture.account(ACCOUNT).currentCycleDebit());
        assertEquals(new BigDecimal("100.25"), fixture.tranCatBal(ACCOUNT + "010001").balance());
        assertTrue(fixture.displayed().stream()
                .anyMatch(line -> line.equals("TCATBAL record not found for key : "
                        + ACCOUNT + "010001.. Creating.")));

        TranRecord posted = batch.transactFile().get(0);
        assertEquals("0000000000000001", posted.id());
        assertEquals(new BigDecimal("100.25"), posted.amount());
        assertEquals(BatchFixture.PINNED_PROC_TS, posted.procTs());
        assertEquals(ORIG_TS, posted.origTs());
    }

    @Test
    void postsDebitToTheCycleDebitBucket() {
        BatchFixture fixture = fixtureWithAccount(new BigDecimal("50.00"), new BigDecimal("1000.00"),
                new BigDecimal("10.00"), new BigDecimal("5.00"), "2025-05-20");
        PostTranBatch batch = fixture.batch();

        batch.run(List.of(tran(new BigDecimal("-20.50"))));

        assertEquals(new BigDecimal("29.50"), fixture.account(ACCOUNT).currentBalance());
        assertEquals(new BigDecimal("10.00"), fixture.account(ACCOUNT).currentCycleCredit());
        assertEquals(new BigDecimal("-15.50"), fixture.account(ACCOUNT).currentCycleDebit());
    }

    @Test
    void accumulatesIntoAnExistingCategoryBalance() {
        BatchFixture fixture = fixtureWithAccount(new BigDecimal("0.00"), new BigDecimal("100000.00"),
                new BigDecimal("0.00"), new BigDecimal("0.00"), "2025-05-20")
                .withTranCatBal(RecordImages.tranCatBal(ACCOUNT, "01", "0001", new BigDecimal("40.00")));
        PostTranBatch batch = fixture.batch();

        batch.run(List.of(
                tran("0000000000000001", "01", "0001", new BigDecimal("10.00"), CARD),
                tran("0000000000000002", "01", "0001", new BigDecimal("2.50"), CARD)));

        assertEquals(new BigDecimal("52.50"), fixture.tranCatBal(ACCOUNT + "010001").balance());
        assertTrue(fixture.displayed().stream().noneMatch(line -> line.startsWith("TCATBAL record not found")));
    }

    @Test
    void createdCategoryBalanceInheritsTheFillerOfTheLastRecordRead() {
        // INITIALIZE TRAN-CAT-BAL-RECORD does not touch FILLER, so a created record keeps the
        // FILLER bytes left in the record area by the previous READ (zeroes in the sample data).
        BatchFixture fixture = fixtureWithAccount(new BigDecimal("0.00"), new BigDecimal("100000.00"),
                new BigDecimal("0.00"), new BigDecimal("0.00"), "2025-05-20")
                .withTranCatBal(RecordImages.tranCatBal(ACCOUNT, "01", "0001", new BigDecimal("40.00"))
                        .substring(0, 28) + "0".repeat(22));
        PostTranBatch batch = fixture.batch();

        batch.run(List.of(
                tran("0000000000000001", "01", "0001", new BigDecimal("1.00"), CARD),
                tran("0000000000000002", "03", "0001", new BigDecimal("2.00"), CARD)));

        assertEquals("0".repeat(22), fixture.tranCatBal(ACCOUNT + "030001").filler());
        assertEquals(2, batch.transactFile().size());
    }

    @Test
    void countsTransactionsAndSetsReturnCodeFourOnlyWhenSomethingWasRejected() {
        BatchFixture fixture = fixtureWithAccount(new BigDecimal("0.00"), new BigDecimal("1000.00"),
                new BigDecimal("0.00"), new BigDecimal("0.00"), "2025-05-20");
        PostTranBatch batch = fixture.batch();

        PostTranBatch.Result result = batch.run(List.of(
                tran("0000000000000001", "01", "0001", new BigDecimal("10.00"), CARD),
                tran("0000000000000002", "01", "0001", new BigDecimal("10.00"), "9999999999999999")));

        assertEquals(new PostTranBatch.Result(2, 1, 4), result);
        assertEquals(1, batch.transactFile().size());
        assertEquals(1, batch.rejectFile().size());
        assertEquals(List.of(
                        "START OF EXECUTION OF PROGRAM CBTRN02C",
                        "TCATBAL record not found for key : " + ACCOUNT + "010001.. Creating.",
                        "TRANSACTIONS PROCESSED :000000002",
                        "TRANSACTIONS REJECTED  :000000001",
                        "END OF EXECUTION OF PROGRAM CBTRN02C"),
                fixture.displayed());
    }

    @Test
    void rejectRecordCarriesTheDailyTransactionImageVerbatim() {
        DalyTranRecord dalyTran = tran(new BigDecimal("10.00"));
        PostTranBatch batch = BatchFixture.empty().batch();

        batch.run(List.of(dalyTran));

        RejectRecord reject = batch.rejectFile().get(0);
        assertEquals(dalyTran.raw(), reject.tranData());
        assertEquals(RejectRecord.LENGTH, reject.serialize().length());
        assertEquals("0100", reject.serialize().substring(350, 354));
    }
}
