package com.aws.carddemo.cbact04c.service;

import com.aws.carddemo.cbact04c.io.CardDemoDataLoader;
import com.aws.carddemo.cbact04c.model.TranCatBalRecord;
import com.aws.carddemo.cbact04c.repository.InMemoryAccountRepository;
import com.aws.carddemo.cbact04c.repository.InMemoryCardXrefRepository;
import com.aws.carddemo.cbact04c.repository.InMemoryDisclosureGroupRepository;
import com.aws.carddemo.cbact04c.repository.ListTransactionWriter;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the service with the real CardDemo ASCII sample files (copied from
 * app/data). Every sample category balance is 0.00, so a faithful run produces
 * no interest transactions -- this exercises full-file parsing end to end.
 */
class SampleDataEndToEndTest {

    private Path sample(String name) throws Exception {
        return Path.of(getClass().getResource("/sample/" + name).toURI());
    }

    @Test
    void runsOverFullSampleDataSet() throws Exception {
        List<TranCatBalRecord> balances = CardDemoDataLoader.loadTranCatBal(sample("tcatbal.txt"));
        InMemoryAccountRepository accounts = CardDemoDataLoader.loadAccounts(sample("acctdata.txt"));
        InMemoryCardXrefRepository xrefs = CardDemoDataLoader.loadCardXref(sample("cardxref.txt"));
        InMemoryDisclosureGroupRepository groups = CardDemoDataLoader.loadDisclosureGroups(sample("discgrp.txt"));
        ListTransactionWriter writer = new ListTransactionWriter();

        InterestCalculatorService service = new InterestCalculatorService(
                accounts, xrefs, groups, writer, () -> "2022-07-28-00.00.00.000000");
        InterestCalculationResult result = service.run(balances, "2022071800");

        assertEquals(50, result.getRecordCount());
        // Every sample balance is 0.00 but the matching rates are non-zero, so -- faithfully
        // to the COBOL -- a 0.00 interest transaction is written for every record.
        assertEquals(50, result.getTransactionsWritten());
        assertTrue(writer.getWritten().stream()
                .allMatch(t -> t.getAmount().compareTo(new java.math.BigDecimal("0.00")) == 0));
    }
}
