package com.carddemo.intcalc.files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.carddemo.intcalc.Account;
import com.carddemo.intcalc.CardXref;
import com.carddemo.intcalc.DiscGroup;
import com.carddemo.intcalc.DiscGroupKey;
import com.carddemo.intcalc.TranCatBalance;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Tests the copybook layouts against the sample datasets in app/data/ASCII. */
class LayoutsTest {

    private static final Path DATA_DIR = Path.of("..", "..", "app", "data", "ASCII");

    private static List<String> records(String name, int length) throws IOException {
        return Files.readAllLines(DATA_DIR.resolve(name), StandardCharsets.ISO_8859_1).stream()
                .map(line -> line.replace("\r", ""))
                .filter(line -> !line.isEmpty())
                .map(line -> line.length() >= length ? line.substring(0, length)
                        : line + " ".repeat(length - line.length()))
                .toList();
    }

    @Test
    void everyCategoryBalanceRecordSurvivesACodecRoundTrip() throws IOException {
        for (String record : records("tcatbal.txt", Layouts.TRAN_CAT_BAL_LENGTH)) {
            assertEquals(record, Layouts.tranCatBalance(Layouts.tranCatBalance(record)));
        }
    }

    @Test
    void everyDisclosureGroupRecordSurvivesACodecRoundTrip() throws IOException {
        for (String record : records("discgrp.txt", Layouts.DISC_GROUP_LENGTH)) {
            assertEquals(record, Layouts.discGroup(Layouts.discGroup(record), Layouts.discGroupFiller(record)));
        }
    }

    @Test
    void everyAccountRecordSurvivesACodecRoundTrip() throws IOException {
        for (String record : records("acctdata.txt", Layouts.ACCOUNT_LENGTH)) {
            assertEquals(record, Layouts.account(Layouts.account(record), Layouts.accountFiller(record)));
        }
    }

    @Test
    void everyCrossReferenceRecordSurvivesACodecRoundTrip() throws IOException {
        for (String record : records("cardxref.txt", Layouts.XREF_LENGTH)) {
            assertEquals(record, Layouts.xref(Layouts.xref(record)));
        }
    }

    @Test
    void readsTheFieldsOfACategoryBalanceRecord() {
        TranCatBalance balance = Layouts.tranCatBalance("000000000420100030000123456A0000000000000000000000");
        assertEquals(42, balance.getAcctId());
        assertEquals("01", balance.getTypeCd());
        assertEquals("0003", balance.getCatCd());
        assertEquals(new BigDecimal("12345.61"), balance.getBalance());
        assertEquals("0000000000000000000000", balance.getFiller());
    }

    @Test
    void readsTheFieldsOfADisclosureGroupRecord() {
        DiscGroup group = Layouts.discGroup("DEFAULT   01000100150{0000000000000000000000000000");
        assertEquals(new DiscGroupKey("DEFAULT", "01", "0001"), group.key());
        assertEquals(new BigDecimal("15.00"), group.intRate());
        assertEquals("DEFAULT   010001", group.key().image());
    }

    @Test
    void keepsTheAccountFillerOfARewrittenRecord() throws IOException {
        List<String> images = records("acctdata.txt", Layouts.ACCOUNT_LENGTH);
        FlatFileAccountRepository repository = new FlatFileAccountRepository(images);
        Account account = repository.find(1).orElseThrow();
        account.setCurrBal(account.getCurrBal().add(new BigDecimal("10.00")));
        repository.rewrite(account);

        String rewritten = repository.records().get(0);
        assertEquals(Layouts.accountFiller(images.get(0)), Layouts.accountFiller(rewritten));
        assertEquals(1, repository.rewriteCount());
    }

    @Test
    void dumpsTheAccountFileInKeySequence() throws IOException {
        FlatFileAccountRepository repository =
                new FlatFileAccountRepository(records("acctdata.txt", Layouts.ACCOUNT_LENGTH));
        List<String> dumped = repository.records();
        for (int i = 1; i < dumped.size(); i++) {
            assertTrue(dumped.get(i - 1).substring(0, 11).compareTo(dumped.get(i).substring(0, 11)) < 0);
        }
    }

    @Test
    void readsTheCrossReferenceThroughTheAccountIdAlternateKey() throws IOException {
        FlatFileXrefRepository repository = new FlatFileXrefRepository(records("cardxref.txt", Layouts.XREF_LENGTH));
        Optional<CardXref> found = repository.findByAcctId(1);
        assertEquals("9680294154603697", found.orElseThrow().cardNum());
        assertEquals(Optional.empty(), repository.findByAcctId(99999999999L));
    }

    @Test
    void readsTheCategoryBalanceFileInKeySequence() throws IOException {
        FlatFileTranCatBalanceReader reader =
                new FlatFileTranCatBalanceReader(records("tcatbal.txt", Layouts.TRAN_CAT_BAL_LENGTH));
        assertEquals(50, reader.records().size());
        assertEquals(1, reader.next().orElseThrow().getAcctId());
        for (int i = 1; i < 50; i++) {
            reader.next();
        }
        assertEquals(Optional.empty(), reader.next());
    }
}
