package com.carddemo.interest;

import com.carddemo.interest.cobol.Zoned;
import com.carddemo.interest.records.AccountRecord;
import com.carddemo.interest.records.CardXrefRecord;
import com.carddemo.interest.records.DisclosureGroupRecord;
import com.carddemo.interest.records.TranCatBalRecord;
import com.carddemo.interest.records.TransactionRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterestCalculatorTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2022-07-18T10:15:30.42Z"), ZoneOffset.UTC);

    private final List<AccountRecord> accounts = new ArrayList<>();
    private final List<CardXrefRecord> xrefs = new ArrayList<>();
    private final List<DisclosureGroupRecord> rates = new ArrayList<>();

    @Test
    void computesOneMonthOfInterestAndPostsItToTheAccount() {
        account(1L, "1000.00", "A000000001");
        xref(1L, "4111111111111111");
        rate("A000000001", "01", "0001", "15.00");

        InterestCalculator calculator = calculator(false);
        InterestCalculator.Result result = calculator.run(List.of(balance(1L, "01", "0001", "1000.00")));

        assertEquals(1, result.transactionsWritten());
        assertEquals(1, result.accountsUpdated());
        // 1000.00 * 15.00 / 1200 = 12.50
        assertEquals(new BigDecimal("12.50"), calculator.transactions().get(0).amount());
        assertEquals(new BigDecimal("1012.50"), calculator.updatedAccounts().get(0).currentBalance());
    }

    @Test
    void truncatesRatherThanRoundsEachCategory() {
        account(1L, "0.00", "A000000001");
        xref(1L, "4111111111111111");
        rate("A000000001", "01", "0001", "15.00");

        InterestCalculator calculator = calculator(false);
        // 100.07 * 15.00 / 1200 = 1.250875 -> 1.25 (COBOL COMPUTE has no ROUNDED clause)
        calculator.run(List.of(balance(1L, "01", "0001", "100.07")));

        assertEquals(new BigDecimal("1.25"), calculator.transactions().get(0).amount());
    }

    @Test
    void producesNegativeInterestForCreditBalances() {
        account(1L, "0.00", "A000000001");
        xref(1L, "4111111111111111");
        rate("A000000001", "01", "0001", "15.00");

        InterestCalculator calculator = calculator(false);
        calculator.run(List.of(balance(1L, "01", "0001", "-1000.00")));

        assertEquals(new BigDecimal("-12.50"), calculator.transactions().get(0).amount());
        assertEquals(new BigDecimal("-12.50"), calculator.updatedAccounts().get(0).currentBalance());
    }

    @Test
    void skipsCategoriesWithAZeroRate() {
        account(1L, "500.00", "A000000001");
        xref(1L, "4111111111111111");
        rate("A000000001", "01", "0001", "0.00");

        InterestCalculator calculator = calculator(false);
        InterestCalculator.Result result = calculator.run(List.of(balance(1L, "01", "0001", "1000.00")));

        assertEquals(0, result.transactionsWritten());
        assertEquals(new BigDecimal("500.00"), calculator.updatedAccounts().get(0).currentBalance());
    }

    @Test
    void fallsBackToTheDefaultDisclosureGroup() {
        account(1L, "0.00", "NOSUCHGRP");
        xref(1L, "4111111111111111");
        rate("DEFAULT", "01", "0001", "24.00");

        InterestCalculator calculator = calculator(false);
        calculator.run(List.of(balance(1L, "01", "0001", "1000.00")));

        assertEquals(new BigDecimal("20.00"), calculator.transactions().get(0).amount());
    }

    @Test
    void abendsWhenEvenTheDefaultGroupIsMissing() {
        account(1L, "0.00", "NOSUCHGRP");
        xref(1L, "4111111111111111");

        InterestCalculator calculator = calculator(false);
        AbendException abend = assertThrows(AbendException.class,
                () -> calculator.run(List.of(balance(1L, "01", "0001", "1000.00"))));
        assertTrue(abend.getMessage().contains("DEFAULT DISCLOSURE GROUP"));
    }

    @Test
    void abendsWhenTheAccountOrXrefIsMissing() {
        rate("A000000001", "01", "0001", "15.00");
        xref(1L, "4111111111111111");
        AbendException noAccount = assertThrows(AbendException.class,
                () -> calculator(false).run(List.of(balance(1L, "01", "0001", "1000.00"))));
        assertTrue(noAccount.getMessage().contains("ACCOUNT NOT FOUND"));

        xrefs.clear();
        account(1L, "0.00", "A000000001");
        AbendException noXref = assertThrows(AbendException.class,
                () -> calculator(false).run(List.of(balance(1L, "01", "0001", "1000.00"))));
        assertTrue(noXref.getMessage().contains("XREF ACCOUNT NOT FOUND"));
    }

    @Test
    void sumsEveryCategoryOfAnAccountBeforePostingOnTheAccountBreak() {
        account(1L, "100.00", "A000000001");
        account(2L, "200.00", "A000000001");
        xref(1L, "4111111111111111");
        xref(2L, "4222222222222222");
        rate("A000000001", "01", "0001", "12.00");
        rate("A000000001", "01", "0002", "24.00");

        InterestCalculator calculator = calculator(false);
        InterestCalculator.Result result = calculator.run(List.of(
                balance(1L, "01", "0001", "1000.00"),
                balance(1L, "01", "0002", "1000.00"),
                balance(2L, "01", "0001", "1000.00")));

        assertEquals(3, result.transactionsWritten());
        assertEquals(2, result.accountsUpdated());
        // account 1: 10.00 + 20.00 on top of 100.00
        assertEquals(new BigDecimal("130.00"), calculator.updatedAccounts().get(0).currentBalance());
        assertEquals(new BigDecimal("210.00"), calculator.updatedAccounts().get(1).currentBalance());
    }

    @Test
    void closesTheBillingCycleWhenItPostsInterest() {
        AccountRecord account = account(1L, "100.00", "A000000001");
        xref(1L, "4111111111111111");
        rate("A000000001", "01", "0001", "12.00");

        calculator(false).run(List.of(balance(1L, "01", "0001", "1000.00")));

        assertEquals(new BigDecimal("0.00"), account.currentCycleCredit());
        assertEquals(new BigDecimal("0.00"), account.currentCycleDebit());
    }

    @Test
    void quirkModeLeavesTheFinalAccountMasterUntouched() {
        account(1L, "100.00", "A000000001");
        account(2L, "200.00", "A000000001");
        xref(1L, "4111111111111111");
        xref(2L, "4222222222222222");
        rate("A000000001", "01", "0001", "12.00");

        InterestCalculator calculator = calculator(true);
        InterestCalculator.Result result = calculator.run(List.of(
                balance(1L, "01", "0001", "1000.00"),
                balance(2L, "01", "0001", "1000.00")));

        assertEquals(2, result.transactionsWritten());
        assertEquals(1, result.accountsUpdated());
        assertEquals(new BigDecimal("110.00"), calculator.updatedAccounts().get(0).currentBalance());
        // account 2 got a transaction but no balance update - the COBOL defect
        assertEquals(new BigDecimal("200.00"), calculator.updatedAccounts().get(1).currentBalance());
    }

    @Test
    void stampsTheGeneratedTransactionLikeTheCobolDoes() {
        account(7L, "0.00", "A000000001");
        xref(7L, "4111111111111111");
        rate("A000000001", "01", "0001", "12.00");

        InterestCalculator calculator = calculator(false);
        calculator.run(List.of(balance(7L, "01", "0001", "1000.00"), balance(7L, "01", "0001", "1000.00")));

        TransactionRecord first = calculator.transactions().get(0);
        assertEquals("2022071800000001", first.transactionId());
        assertEquals("2022071800000002", calculator.transactions().get(1).transactionId());
        assertEquals("01", first.typeCode());
        assertEquals("0005", first.categoryCode());
        assertEquals("System", first.source());
        assertEquals("Int. for a/c 00000000007", first.description());
        assertEquals("4111111111111111", first.cardNumber());
        assertEquals(0L, first.merchantId());
        assertEquals("2022-07-18-10.15.30.420000", first.originTimestamp());
        assertEquals(first.originTimestamp(), first.processTimestamp());
        assertEquals(TransactionRecord.LENGTH, first.toRecord().length());
    }

    private InterestCalculator calculator(boolean emulateQuirk) {
        return new InterestCalculator(
                new InterestCalculator.Options("2022071800", FIXED_CLOCK, emulateQuirk),
                InterestCalculator.indexAccounts(accounts),
                InterestCalculator.indexXrefsByAccount(xrefs),
                InterestCalculator.indexDisclosureGroups(rates));
    }

    private AccountRecord account(long id, String balance, String groupId) {
        String record = Zoned.formatUnsigned(id, 11)
                + "Y"
                + Zoned.formatSigned(new BigDecimal(balance), 12, 2)
                + Zoned.formatSigned(new BigDecimal("5000.00"), 12, 2)
                + Zoned.formatSigned(new BigDecimal("1000.00"), 12, 2)
                + "2014-11-20" + "2025-05-20" + "2025-05-20"
                + Zoned.formatSigned(new BigDecimal("11.11"), 12, 2)
                + Zoned.formatSigned(new BigDecimal("22.22"), 12, 2)
                + Zoned.alphanumeric("12345", 10)
                + Zoned.alphanumeric(groupId, 10)
                + " ".repeat(AccountRecord.LENGTH - 122);
        AccountRecord parsed = AccountRecord.parse(record);
        accounts.add(parsed);
        return parsed;
    }

    private void xref(long accountId, String cardNumber) {
        xrefs.add(new CardXrefRecord(cardNumber, 100 + accountId, accountId));
    }

    private void rate(String groupId, String typeCode, String categoryCode, String annualRate) {
        rates.add(new DisclosureGroupRecord(Zoned.alphanumeric(groupId, 10), typeCode, categoryCode,
                new BigDecimal(annualRate)));
    }

    private TranCatBalRecord balance(long accountId, String typeCode, String categoryCode, String amount) {
        return new TranCatBalRecord(accountId, typeCode, categoryCode, new BigDecimal(amount), "");
    }

    @Test
    void mapsAreBuiltFromRecordsWithoutLosingOrder() {
        account(3L, "1.00", "A000000001");
        account(1L, "2.00", "A000000001");
        Map<Long, AccountRecord> index = InterestCalculator.indexAccounts(accounts);
        assertEquals(List.of(3L, 1L), List.copyOf(index.keySet()));
    }
}
