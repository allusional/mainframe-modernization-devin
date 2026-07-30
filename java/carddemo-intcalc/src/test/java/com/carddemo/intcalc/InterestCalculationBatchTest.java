package com.carddemo.intcalc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for the ported PROCEDURE DIVISION of CBACT04C. */
class InterestCalculationBatchTest {

    private static final String PARM_DATE = "2022071800";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2024-03-15T10:20:30.45Z"), ZoneOffset.UTC);

    private final List<String> displayed = new ArrayList<>();
    private InMemoryAccountRepository accounts;
    private InMemoryTransactionWriter transactions;

    private InterestCalculationBatch batch(List<TranCatBalance> balances,
                                          List<Account> accountList,
                                          List<DiscGroup> groups,
                                          List<CardXref> xrefs) {
        accounts = new InMemoryAccountRepository(accountList);
        transactions = new InMemoryTransactionWriter();
        return new InterestCalculationBatch(new InMemoryTranCatBalanceReader(balances),
                new InMemoryXrefRepository(xrefs),
                new InMemoryDiscGroupRepository(groups),
                accounts,
                transactions,
                new Db2Timestamp(CLOCK),
                displayed::add,
                PARM_DATE);
    }

    private static Account account(long id, String groupId, String balance) {
        Account account = new Account();
        account.setAcctId(id);
        account.setActiveStatus("Y");
        account.setGroupId(groupId);
        account.setCurrBal(new BigDecimal(balance));
        account.setCurrCycCredit(new BigDecimal("111.11"));
        account.setCurrCycDebit(new BigDecimal("222.22"));
        return account;
    }

    private static TranCatBalance balance(long acctId, String catCd, String amount) {
        return new TranCatBalance(acctId, "01", catCd, new BigDecimal(amount));
    }

    private static DiscGroup group(String groupId, String catCd, String rate) {
        return new DiscGroup(new DiscGroupKey(groupId, "01", catCd), new BigDecimal(rate));
    }

    private static CardXref xref(long acctId, String cardNum) {
        return new CardXref(cardNum, "000000001", acctId);
    }

    @Test
    void computesMonthlyInterestAndWritesOneTransactionPerCategoryBalance() {
        InterestCalculationBatch batch = batch(
                List.of(balance(1, "0001", "1200.00"), balance(1, "0002", "600.00")),
                List.of(account(1, "GRP1", "100.00")),
                List.of(group("GRP1", "0001", "12.00"), group("GRP1", "0002", "24.00")),
                List.of(xref(1, "4111111111111111")));

        assertEquals(0, batch.run());
        assertEquals(2, batch.getRecordCount());
        assertEquals(2, transactions.written().size());

        Transaction first = transactions.written().get(0);
        assertEquals("2022071800000001", first.getId());
        assertEquals("01", first.getTypeCd());
        assertEquals("0005", first.getCatCd());
        assertEquals("System", first.getSource());
        assertEquals("Int. for a/c 00000000001", first.getDesc());
        assertEquals("4111111111111111", first.getCardNum());
        assertEquals("000000000", first.getMerchantId());
        assertEquals("2024-03-15-10.20.30.450000", first.getOrigTs());
        assertEquals(first.getOrigTs(), first.getProcTs());
        // 1200.00 * 12.00 / 1200
        assertEquals(new BigDecimal("12.00"), first.getAmt());
        // 600.00 * 24.00 / 1200
        assertEquals(new BigDecimal("12.00"), transactions.written().get(1).getAmt());
        assertEquals("2022071800000002", transactions.written().get(1).getId());
        assertEquals(new BigDecimal("24.00"), batch.getTotalInterest());
    }

    @Test
    void addsTheInterestOfAnAccountToItsBalanceWhenTheAccountChanges() {
        InterestCalculationBatch batch = batch(
                List.of(balance(1, "0001", "1200.00"), balance(2, "0001", "2400.00")),
                List.of(account(1, "GRP1", "100.00"), account(2, "GRP1", "200.00")),
                List.of(group("GRP1", "0001", "12.00")),
                List.of(xref(1, "4111111111111111"), xref(2, "4222222222222222")));

        assertEquals(0, batch.run());

        Account first = accounts.find(1).orElseThrow();
        assertEquals(new BigDecimal("112.00"), first.getCurrBal());
        assertEquals(new BigDecimal("0.00"), first.getCurrCycCredit());
        assertEquals(new BigDecimal("0.00"), first.getCurrCycDebit());
    }

    /**
     * The ELSE branch of the main loop that would rewrite the last account is unreachable, so the
     * interest of the last account on the file is written to TRANFILE but never added to the
     * account balance. This test pins that defect down.
     */
    @Test
    void neverUpdatesTheAccountOfTheLastRecordOnTheFile() {
        InterestCalculationBatch batch = batch(
                List.of(balance(1, "0001", "1200.00"), balance(2, "0001", "2400.00")),
                List.of(account(1, "GRP1", "100.00"), account(2, "GRP1", "200.00")),
                List.of(group("GRP1", "0001", "12.00")),
                List.of(xref(1, "4111111111111111"), xref(2, "4222222222222222")));

        batch.run();

        Account last = accounts.find(2).orElseThrow();
        assertEquals(new BigDecimal("200.00"), last.getCurrBal());
        assertEquals(new BigDecimal("111.11"), last.getCurrCycCredit());
        assertEquals(new BigDecimal("222.22"), last.getCurrCycDebit());
        assertEquals(new BigDecimal("24.00"), transactions.written().get(1).getAmt());
    }

    @Test
    void writesNoTransactionWhenTheInterestRateIsZero() {
        InterestCalculationBatch batch = batch(
                List.of(balance(1, "0001", "1200.00"), balance(2, "0001", "2400.00")),
                List.of(account(1, "ZEROAPR", "100.00"), account(2, "ZEROAPR", "200.00")),
                List.of(group("ZEROAPR", "0001", "0.00")),
                List.of(xref(1, "4111111111111111"), xref(2, "4222222222222222")));

        assertEquals(0, batch.run());
        assertTrue(transactions.written().isEmpty());
        // The account is still rewritten at the account break: balance unchanged, cycle totals reset.
        Account first = accounts.find(1).orElseThrow();
        assertEquals(new BigDecimal("100.00"), first.getCurrBal());
        assertEquals(new BigDecimal("0.00"), first.getCurrCycCredit());
        assertEquals(new BigDecimal("0.00"), first.getCurrCycDebit());
    }

    @Test
    void fallsBackToTheDefaultDisclosureGroupWhenTheAccountGroupHasNoRecord() {
        InterestCalculationBatch batch = batch(
                List.of(balance(1, "0001", "1200.00")),
                List.of(account(1, "", "100.00")),
                List.of(group("DEFAULT", "0001", "15.00")),
                List.of(xref(1, "4111111111111111")));

        assertEquals(0, batch.run());
        assertEquals(new BigDecimal("15.00"), transactions.written().get(0).getAmt());
        assertTrue(displayed.contains("DISCLOSURE GROUP RECORD MISSING"));
        assertTrue(displayed.contains("TRY WITH DEFAULT GROUP CODE"));
    }

    @Test
    void abendsWhenNeitherTheAccountGroupNorTheDefaultGroupHasARecord() {
        InterestCalculationBatch batch = batch(
                List.of(balance(1, "0001", "1200.00")),
                List.of(account(1, "GRP1", "100.00")),
                List.of(group("DEFAULT", "0002", "15.00")),
                List.of(xref(1, "4111111111111111")));

        AbendException abend = assertThrows(AbendException.class, batch::run);
        assertEquals(999, abend.getAbendCode());
        assertEquals(List.of("000000000010100010000012000{" + " ".repeat(22),
                        "DISCLOSURE GROUP RECORD MISSING",
                        "TRY WITH DEFAULT GROUP CODE",
                        "ERROR READING DEFAULT DISCLOSURE GROUP",
                        "FILE STATUS IS: NNNN0023",
                        "ABENDING PROGRAM"),
                displayed);
    }

    @Test
    void abendsWhenTheAccountRecordIsMissing() {
        InterestCalculationBatch batch = batch(
                List.of(balance(7, "0001", "1200.00")),
                List.of(account(1, "GRP1", "100.00")),
                List.of(group("GRP1", "0001", "12.00")),
                List.of(xref(7, "4111111111111111")));

        assertThrows(AbendException.class, batch::run);
        assertTrue(displayed.contains("ACCOUNT NOT FOUND: 00000000007"));
        assertTrue(displayed.contains("ERROR READING ACCOUNT FILE"));
        assertTrue(displayed.contains("FILE STATUS IS: NNNN0023"));
        assertTrue(displayed.contains("ABENDING PROGRAM"));
    }

    @Test
    void abendsWhenTheCrossReferenceRecordIsMissing() {
        InterestCalculationBatch batch = batch(
                List.of(balance(1, "0001", "1200.00")),
                List.of(account(1, "GRP1", "100.00")),
                List.of(group("GRP1", "0001", "12.00")),
                List.of(xref(9, "4111111111111111")));

        assertThrows(AbendException.class, batch::run);
        assertTrue(displayed.contains("ACCOUNT NOT FOUND: 00000000001"));
        assertTrue(displayed.contains("ERROR READING XREF FILE"));
    }

    @Test
    void truncatesTheMonthlyInterestInsteadOfRoundingIt() {
        InterestCalculationBatch batch = batch(
                List.of(balance(1, "0001", "100.01")),
                List.of(account(1, "GRP1", "0.00")),
                List.of(group("GRP1", "0001", "15.00")),
                List.of(xref(1, "4111111111111111")));

        batch.run();
        // 100.01 * 15 / 1200 = 1.250125
        assertEquals(new BigDecimal("1.25"), transactions.written().get(0).getAmt());
    }

    @Test
    void truncatesNegativeInterestTowardsZero() {
        InterestCalculationBatch batch = batch(
                List.of(balance(1, "0001", "-100.09")),
                List.of(account(1, "GRP1", "0.00")),
                List.of(group("GRP1", "0001", "15.00")),
                List.of(xref(1, "4111111111111111")));

        batch.run();
        // -100.09 * 15 / 1200 = -1.251125
        assertEquals(new BigDecimal("-1.25"), transactions.written().get(0).getAmt());
    }

    @Test
    void keepsProcessingTheSameAccountWithoutRereadingItsAccountRecord() {
        InterestCalculationBatch batch = batch(
                List.of(balance(1, "0001", "1200.00"), balance(1, "0002", "1200.00"), balance(2, "0001", "1200.00")),
                List.of(account(1, "GRP1", "0.00"), account(2, "GRP1", "0.00")),
                List.of(group("GRP1", "0001", "12.00"), group("GRP1", "0002", "12.00")),
                List.of(xref(1, "4111111111111111"), xref(2, "4222222222222222")));

        batch.run();
        assertEquals(new BigDecimal("24.00"), accounts.find(1).orElseThrow().getCurrBal());
        assertEquals(List.of("4111111111111111", "4111111111111111", "4222222222222222"),
                transactions.written().stream().map(Transaction::getCardNum).toList());
    }

    @Test
    void displaysEveryCategoryBalanceRecordItReads() {
        InterestCalculationBatch batch = batch(
                List.of(new TranCatBalance(1, "01", "0001", new BigDecimal("1200.00"), "0000000000000000000000")),
                List.of(account(1, "GRP1", "0.00")),
                List.of(group("GRP1", "0001", "12.00")),
                List.of(xref(1, "4111111111111111")));

        batch.run();
        assertEquals("000000000010100010000012000{0000000000000000000000", displayed.get(0));
        assertEquals(50, displayed.get(0).length());
    }
}
