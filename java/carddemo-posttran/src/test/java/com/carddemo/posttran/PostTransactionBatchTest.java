package com.carddemo.posttran;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostTransactionBatchTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2024-03-15T10:20:30.456Z"), ZoneId.of("UTC"));

    private final InMemoryXrefRepository xrefs = new InMemoryXrefRepository();
    private final InMemoryAccountRepository accounts = new InMemoryAccountRepository();
    private final InMemoryTranCatBalanceRepository tranCatBalances = new InMemoryTranCatBalanceRepository();
    private final InMemoryTransactionWriter transactions = new InMemoryTransactionWriter();
    private final InMemoryRejectWriter rejects = new InMemoryRejectWriter();

    private PostTransactionBatch batch(List<DailyTransaction> input) {
        return new PostTransactionBatch(
                new InMemoryDailyTransactionReader(input),
                xrefs,
                accounts,
                tranCatBalances,
                transactions,
                rejects,
                new Db2Timestamp(FIXED_CLOCK));
    }

    private static DailyTransaction daily(String id, String amount) {
        DailyTransaction daily = new DailyTransaction();
        daily.setId(id);
        daily.setTypeCd("01");
        daily.setCatCd("5411");
        daily.setSource("POS");
        daily.setDesc("GROCERIES");
        daily.setAmt(new BigDecimal(amount));
        daily.setMerchantId("000123456");
        daily.setMerchantName("ACME MARKET");
        daily.setMerchantCity("SEATTLE");
        daily.setMerchantZip("98101");
        daily.setCardNum("4111111111111111");
        daily.setOrigTs("2024-03-14-08.15.00.000000");
        return daily;
    }

    private Account account(long acctId, String currBal, String creditLimit, String expiration) {
        Account account = new Account();
        account.setAcctId(acctId);
        account.setActiveStatus("Y");
        account.setCurrBal(new BigDecimal(currBal));
        account.setCreditLimit(new BigDecimal(creditLimit));
        account.setCashCreditLimit(new BigDecimal("1000.00"));
        account.setOpenDate("2020-01-01");
        account.setExpirationDate(expiration);
        account.setReissueDate("2023-01-01");
        account.setCurrCycCredit(new BigDecimal("100.00"));
        account.setCurrCycDebit(new BigDecimal("25.00"));
        account.setAddrZip("98101");
        account.setGroupId("GRP0000001");
        accounts.put(account);
        return account;
    }

    private void xref(long acctId) {
        xrefs.put(new CardXref("4111111111111111", "000000001", acctId));
    }

    @Test
    void postsValidTransactionAndUpdatesAccountCredit() {
        xref(11111111111L);
        Account account = account(11111111111L, "500.00", "5000.00", "2030-12-31");

        PostTransactionBatch batch = batch(List.of(daily("TRAN0000000000001", "150.25")));
        int returnCode = batch.run();

        assertEquals(0, returnCode);
        assertEquals(1, batch.getTransactionCount());
        assertEquals(0, batch.getRejectCount());
        assertTrue(rejects.written().isEmpty());

        assertEquals(1, transactions.written().size());
        Transaction posted = transactions.written().get(0);
        assertEquals("TRAN0000000000001", posted.getId());
        assertEquals("01", posted.getTypeCd());
        assertEquals("5411", posted.getCatCd());
        assertEquals("POS", posted.getSource());
        assertEquals("GROCERIES", posted.getDesc());
        assertEquals(new BigDecimal("150.25"), posted.getAmt());
        assertEquals("000123456", posted.getMerchantId());
        assertEquals("ACME MARKET", posted.getMerchantName());
        assertEquals("SEATTLE", posted.getMerchantCity());
        assertEquals("98101", posted.getMerchantZip());
        assertEquals("4111111111111111", posted.getCardNum());
        assertEquals("2024-03-14-08.15.00.000000", posted.getOrigTs());

        assertEquals(new BigDecimal("650.25"), account.getCurrBal());
        assertEquals(new BigDecimal("250.25"), account.getCurrCycCredit());
        assertEquals(new BigDecimal("25.00"), account.getCurrCycDebit());

        TranCatBalance balance =
                tranCatBalances.find(new TranCatBalanceKey(11111111111L, "01", "5411")).orElseThrow();
        assertEquals(new BigDecimal("150.25"), balance.getBalance());
    }

    @Test
    void negativeAmountUpdatesCycleDebit() {
        xref(11111111111L);
        Account account = account(11111111111L, "500.00", "5000.00", "2030-12-31");

        assertEquals(0, batch(List.of(daily("TRAN0000000000001", "-40.00"))).run());

        assertEquals(new BigDecimal("460.00"), account.getCurrBal());
        assertEquals(new BigDecimal("100.00"), account.getCurrCycCredit());
        assertEquals(new BigDecimal("-15.00"), account.getCurrCycDebit());
    }

    @Test
    void secondTransactionWithSameKeyIncrementsExistingCategoryBalance() {
        xref(11111111111L);
        account(11111111111L, "0.00", "5000.00", "2030-12-31");

        PostTransactionBatch batch = batch(List.of(
                daily("TRAN0000000000001", "10.00"),
                daily("TRAN0000000000002", "15.50")));
        assertEquals(0, batch.run());

        assertEquals(2, transactions.written().size());
        TranCatBalance balance =
                tranCatBalances.find(new TranCatBalanceKey(11111111111L, "01", "5411")).orElseThrow();
        assertEquals(new BigDecimal("25.50"), balance.getBalance());
    }

    @Test
    void rejectsUnknownCardNumber() {
        PostTransactionBatch batch = batch(List.of(daily("TRAN0000000000001", "10.00")));

        assertEquals(4, batch.run());
        assertTrue(transactions.written().isEmpty());
        assertEquals(1, rejects.written().size());
        RejectRecord reject = rejects.written().get(0);
        assertEquals(100, reject.failReason());
        assertEquals("INVALID CARD NUMBER FOUND", reject.failReasonDesc());
        assertEquals("TRAN0000000000001", reject.transaction().getId());
    }

    @Test
    void rejectsMissingAccount() {
        xref(99999999999L);

        PostTransactionBatch batch = batch(List.of(daily("TRAN0000000000001", "10.00")));

        assertEquals(4, batch.run());
        assertTrue(transactions.written().isEmpty());
        assertEquals(101, rejects.written().get(0).failReason());
        assertEquals("ACCOUNT RECORD NOT FOUND", rejects.written().get(0).failReasonDesc());
    }

    @Test
    void rejectsOverlimitTransaction() {
        xref(11111111111L);
        // curr-cyc-credit 100 - curr-cyc-debit 25 + 1000 = 1075 > credit limit 1000
        account(11111111111L, "0.00", "1000.00", "2030-12-31");

        PostTransactionBatch batch = batch(List.of(daily("TRAN0000000000001", "1000.00")));

        assertEquals(4, batch.run());
        assertTrue(transactions.written().isEmpty());
        assertEquals(102, rejects.written().get(0).failReason());
        assertEquals("OVERLIMIT TRANSACTION", rejects.written().get(0).failReasonDesc());
    }

    @Test
    void allowsTransactionExactlyAtCreditLimit() {
        xref(11111111111L);
        account(11111111111L, "0.00", "1000.00", "2030-12-31");

        assertEquals(0, batch(List.of(daily("TRAN0000000000001", "925.00"))).run());
        assertEquals(1, transactions.written().size());
    }

    @Test
    void rejectsTransactionAfterAccountExpiration() {
        xref(11111111111L);
        account(11111111111L, "0.00", "5000.00", "2024-03-13");

        PostTransactionBatch batch = batch(List.of(daily("TRAN0000000000001", "10.00")));

        assertEquals(4, batch.run());
        assertTrue(transactions.written().isEmpty());
        assertEquals(103, rejects.written().get(0).failReason());
        assertEquals("TRANSACTION RECEIVED AFTER ACCT EXPIRATION",
                rejects.written().get(0).failReasonDesc());
    }

    @Test
    void countsProcessedAndRejectedRecords() {
        xref(11111111111L);
        account(11111111111L, "0.00", "5000.00", "2030-12-31");

        DailyTransaction unknownCard = daily("TRAN0000000000003", "10.00");
        unknownCard.setCardNum("4000000000000000");

        PostTransactionBatch batch = batch(List.of(
                daily("TRAN0000000000001", "10.00"),
                daily("TRAN0000000000002", "20.00"),
                unknownCard));

        assertEquals(4, batch.run());
        assertEquals(3, batch.getTransactionCount());
        assertEquals(1, batch.getRejectCount());
        assertEquals(2, transactions.written().size());
        assertEquals(1, rejects.written().size());
    }

    @Test
    void processingTimestampUsesDb2FormatFromInjectedClock() {
        xref(11111111111L);
        account(11111111111L, "0.00", "5000.00", "2030-12-31");

        assertEquals(0, batch(List.of(daily("TRAN0000000000001", "10.00"))).run());

        assertEquals("2024-03-15-10.20.30.450000", transactions.written().get(0).getProcTs());
        assertEquals(26, transactions.written().get(0).getProcTs().length());
    }
}
