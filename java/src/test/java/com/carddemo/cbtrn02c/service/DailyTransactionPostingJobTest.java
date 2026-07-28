package com.carddemo.cbtrn02c.service;

import com.carddemo.cbtrn02c.TestRecords;
import com.carddemo.cbtrn02c.model.CardXrefRecord;
import com.carddemo.cbtrn02c.model.DailyTransactionRecord;
import com.carddemo.cbtrn02c.repository.AccountRepository;
import com.carddemo.cbtrn02c.repository.CardXrefRepository;
import com.carddemo.cbtrn02c.repository.TranCatBalRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DailyTransactionPostingJobTest {

    private static final String CARD = "4859452612877065";
    private static final ProcessingTimestampProvider FIXED_TS = () -> "2022-06-10-19.27.53.000000";

    private DailyTransactionPostingJob jobWith(CardXrefRepository xref, AccountRepository accounts,
                                               TranCatBalRepository tranCatBal) {
        return new DailyTransactionPostingJob(
                new TransactionPostingService(xref, accounts, tranCatBal, FIXED_TS));
    }

    @Test
    void emptyInputProducesZeroCountsAndReturnCodeZero() {
        BatchSummary summary = jobWith(new CardXrefRepository(), new AccountRepository(),
                new TranCatBalRepository()).run(List.of());

        assertEquals(0, summary.getTransactionCount());
        assertEquals(0, summary.getRejectCount());
        assertEquals(0, summary.getReturnCode());
    }

    @Test
    void countsAndReturnCodeReflectMixOfPostedAndRejected() {
        CardXrefRepository xref = new CardXrefRepository();
        xref.put(new CardXrefRecord(CARD, 1L, 55L));
        AccountRepository accounts = new AccountRepository();
        accounts.put(com.carddemo.cbtrn02c.model.AccountRecord.parse(
                TestRecords.account(55L, new BigDecimal("0.00"), new BigDecimal("5000.00"),
                        "2025-12-31", new BigDecimal("0.00"), new BigDecimal("0.00"))));

        DailyTransactionRecord valid = DailyTransactionRecord.parse(
                TestRecords.dailyTran("TXN1", "01", "0001", new BigDecimal("10.00"), CARD, "2022-06-10 00:00:00.000000"));
        DailyTransactionRecord badCard = DailyTransactionRecord.parse(
                TestRecords.dailyTran("TXN2", "01", "0001", new BigDecimal("10.00"), "0000000000000000",
                        "2022-06-10 00:00:00.000000"));

        BatchSummary summary = jobWith(xref, accounts, new TranCatBalRepository())
                .run(List.of(valid, badCard));

        assertEquals(2, summary.getTransactionCount());
        assertEquals(1, summary.getRejectCount());
        assertEquals(1, summary.getPostedTransactions().size());
        assertEquals(4, summary.getReturnCode()); // rejects > 0 -> RETURN-CODE 4
    }
}
