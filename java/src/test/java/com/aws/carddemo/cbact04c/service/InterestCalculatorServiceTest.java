package com.aws.carddemo.cbact04c.service;

import com.aws.carddemo.cbact04c.model.AccountRecord;
import com.aws.carddemo.cbact04c.model.CardXrefRecord;
import com.aws.carddemo.cbact04c.model.DisclosureGroupRecord;
import com.aws.carddemo.cbact04c.model.TranCatBalRecord;
import com.aws.carddemo.cbact04c.model.TransactionRecord;
import com.aws.carddemo.cbact04c.repository.InMemoryAccountRepository;
import com.aws.carddemo.cbact04c.repository.InMemoryCardXrefRepository;
import com.aws.carddemo.cbact04c.repository.InMemoryDisclosureGroupRepository;
import com.aws.carddemo.cbact04c.repository.ListTransactionWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterestCalculatorServiceTest {

    private static final String PARM_DATE = "2022072800";
    private static final String FIXED_TS = "2022-07-28-21.03.45.120000";

    private InMemoryAccountRepository accounts;
    private InMemoryCardXrefRepository xrefs;
    private InMemoryDisclosureGroupRepository groups;
    private ListTransactionWriter writer;

    @BeforeEach
    void setUp() {
        accounts = new InMemoryAccountRepository();
        xrefs = new InMemoryCardXrefRepository();
        groups = new InMemoryDisclosureGroupRepository();
        writer = new ListTransactionWriter();
    }

    private InterestCalculatorService service() {
        return new InterestCalculatorService(accounts, xrefs, groups, writer, () -> FIXED_TS);
    }

    private static AccountRecord account(long id, String group, String balance) {
        return new AccountRecord(id, "Y", money(balance), money("20000.00"), money("5000.00"),
                "2020-01-01", "2030-01-01", "2030-01-01", money("5.00"), money("3.00"),
                "00000", group, "");
    }

    private static BigDecimal money(String v) {
        return new BigDecimal(v).setScale(2);
    }

    private static TranCatBalRecord balance(long acct, String type, int cat, String amount) {
        return new TranCatBalRecord(acct, type, cat, money(amount));
    }

    @Test
    void computesInterestAndPostsAccumulatedInterestWhenAccountChanges() {
        groups.put(new DisclosureGroupRecord("A000000000", "01", 1, money("15.00")));
        accounts.put(account(1L, "A000000000", "100.00"));
        accounts.put(account(2L, "A000000000", "50.00"));
        xrefs.put(new CardXrefRecord("1111111111111111", 10L, 1L));
        xrefs.put(new CardXrefRecord("2222222222222222", 20L, 2L));

        InterestCalculationResult result = service().run(List.of(
                balance(1L, "01", 1, "1000.00"),
                balance(2L, "01", 1, "0.00")), PARM_DATE);

        assertEquals(2, result.getRecordCount());
        assertEquals(2, result.getTransactionsWritten());

        // Account 1 balance posted: 100.00 + (1000.00 * 15 / 1200) = 112.50; cycle totals reset.
        AccountRecord acct1 = accounts.read(1L).orElseThrow();
        assertEquals(money("112.50"), acct1.getCurrentBalance());
        assertEquals(money("0.00"), acct1.getCurrentCycleCredit());
        assertEquals(money("0.00"), acct1.getCurrentCycleDebit());

        TransactionRecord tx = writer.getWritten().get(0);
        assertEquals(money("12.50"), tx.getAmount());
        assertEquals("2022072800000001", tx.getId());
        assertEquals("01", tx.getTypeCode());
        assertEquals(5, tx.getCategoryCode());
        assertEquals("System", tx.getSource());
        assertEquals("Int. for a/c 00000000001", tx.getDescription());
        assertEquals("1111111111111111", tx.getCardNumber());
        assertEquals(FIXED_TS, tx.getOriginationTimestamp());
        assertEquals(FIXED_TS, tx.getProcessingTimestamp());
        assertEquals("2022072800000002", writer.getWritten().get(1).getId());
    }

    @Test
    void lastAccountIsNotPostedReproducingOriginalQuirk() {
        // Faithful reproduction: the final account group's accumulated interest is
        // never posted (the COBOL loop's ELSE branch is unreachable dead code).
        groups.put(new DisclosureGroupRecord("A000000000", "01", 1, money("15.00")));
        accounts.put(account(1L, "A000000000", "100.00"));
        accounts.put(account(2L, "A000000000", "200.00"));
        xrefs.put(new CardXrefRecord("1111111111111111", 10L, 1L));
        xrefs.put(new CardXrefRecord("2222222222222222", 20L, 2L));

        service().run(List.of(
                balance(1L, "01", 1, "1000.00"),
                balance(2L, "01", 1, "1200.00")), PARM_DATE);

        AccountRecord acct2 = accounts.read(2L).orElseThrow();
        // Balance unchanged and cycle totals NOT reset for the last account.
        assertEquals(money("200.00"), acct2.getCurrentBalance());
        assertEquals(money("5.00"), acct2.getCurrentCycleCredit());
        assertEquals(money("3.00"), acct2.getCurrentCycleDebit());
    }

    @Test
    void truncatesInterestTowardZero() {
        groups.put(new DisclosureGroupRecord("A000000000", "01", 1, money("15.00")));
        accounts.put(account(1L, "A000000000", "0.00"));
        xrefs.put(new CardXrefRecord("1111111111111111", 10L, 1L));

        service().run(List.of(balance(1L, "01", 1, "33.33")), PARM_DATE);

        // 33.33 * 15 / 1200 = 0.416625 -> truncated (not rounded) to 0.41
        assertEquals(money("0.41"), writer.getWritten().get(0).getAmount());
    }

    @Test
    void accumulatesMultipleCategoriesForSameAccount() {
        groups.put(new DisclosureGroupRecord("A000000000", "01", 1, money("15.00")));
        groups.put(new DisclosureGroupRecord("A000000000", "01", 2, money("25.00")));
        accounts.put(account(1L, "A000000000", "100.00"));
        accounts.put(account(2L, "A000000000", "0.00"));
        xrefs.put(new CardXrefRecord("1111111111111111", 10L, 1L));
        xrefs.put(new CardXrefRecord("2222222222222222", 20L, 2L));

        service().run(List.of(
                balance(1L, "01", 1, "1000.00"),   // 12.50
                balance(1L, "01", 2, "1000.00"),   // 20.833.. -> 20.83
                balance(2L, "01", 1, "0.00")), PARM_DATE);

        AccountRecord acct1 = accounts.read(1L).orElseThrow();
        // 100.00 + 12.50 + 20.83 = 133.33
        assertEquals(money("133.33"), acct1.getCurrentBalance());
        assertEquals("2022072800000001", writer.getWritten().get(0).getId());
        assertEquals("2022072800000002", writer.getWritten().get(1).getId());
    }

    @Test
    void zeroInterestRateProducesNoTransaction() {
        groups.put(new DisclosureGroupRecord("A000000000", "01", 1, money("0.00")));
        accounts.put(account(1L, "A000000000", "100.00"));
        accounts.put(account(2L, "A000000000", "100.00"));
        xrefs.put(new CardXrefRecord("1111111111111111", 10L, 1L));
        xrefs.put(new CardXrefRecord("2222222222222222", 20L, 2L));

        InterestCalculationResult result = service().run(List.of(
                balance(1L, "01", 1, "1000.00"),
                balance(2L, "01", 1, "1000.00")), PARM_DATE);

        assertTrue(writer.getWritten().isEmpty());
        assertEquals(0, result.getTransactionsWritten());
        // Account 1 was still updated on account change, but with zero interest.
        assertEquals(money("100.00"), accounts.read(1L).orElseThrow().getCurrentBalance());
        assertEquals(money("0.00"), accounts.read(1L).orElseThrow().getCurrentCycleCredit());
    }

    @Test
    void fallsBackToDefaultDisclosureGroupWhenPrimaryMissing() {
        groups.put(new DisclosureGroupRecord("DEFAULT", "01", 1, money("25.00")));
        accounts.put(account(1L, "ZZZZZZZZZZ", "0.00"));
        xrefs.put(new CardXrefRecord("1111111111111111", 10L, 1L));

        service().run(List.of(balance(1L, "01", 1, "100.00")), PARM_DATE);

        // 100.00 * 25 / 1200 = 2.0833.. -> 2.08
        assertEquals(money("2.08"), writer.getWritten().get(0).getAmount());
    }

    @Test
    void missingAccountAbends() {
        groups.put(new DisclosureGroupRecord("A000000000", "01", 1, money("15.00")));
        xrefs.put(new CardXrefRecord("1111111111111111", 10L, 1L));

        AbendException ex = assertThrows(AbendException.class,
                () -> service().run(List.of(balance(1L, "01", 1, "100.00")), PARM_DATE));
        assertTrue(ex.getMessage().contains("ACCOUNT"));
    }

    @Test
    void missingXrefAbends() {
        groups.put(new DisclosureGroupRecord("A000000000", "01", 1, money("15.00")));
        accounts.put(account(1L, "A000000000", "0.00"));

        assertThrows(AbendException.class,
                () -> service().run(List.of(balance(1L, "01", 1, "100.00")), PARM_DATE));
    }

    @Test
    void missingDefaultGroupAbends() {
        accounts.put(account(1L, "ZZZZZZZZZZ", "0.00"));
        xrefs.put(new CardXrefRecord("1111111111111111", 10L, 1L));

        assertThrows(AbendException.class,
                () -> service().run(List.of(balance(1L, "01", 1, "100.00")), PARM_DATE));
    }

    @Test
    void emptyFileProcessesNothing() {
        InterestCalculationResult result = service().run(List.of(), PARM_DATE);
        assertEquals(0, result.getRecordCount());
        assertEquals(0, result.getTransactionsWritten());
        assertTrue(writer.getWritten().isEmpty());
    }
}
