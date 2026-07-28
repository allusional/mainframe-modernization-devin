package com.carddemo.cbtrn02c;

import com.carddemo.cbtrn02c.io.RecordFiles;
import com.carddemo.cbtrn02c.model.AccountRecord;
import com.carddemo.cbtrn02c.model.DailyTransactionRecord;
import com.carddemo.cbtrn02c.repository.AccountRepository;
import com.carddemo.cbtrn02c.repository.CardXrefRepository;
import com.carddemo.cbtrn02c.repository.TranCatBalRepository;
import com.carddemo.cbtrn02c.service.BatchSummary;
import com.carddemo.cbtrn02c.service.DailyTransactionPostingJob;
import com.carddemo.cbtrn02c.service.ProcessingTimestampProvider;
import com.carddemo.cbtrn02c.service.TransactionPostingService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the modernized batch against the real CardDemo ASCII datasets in
 * {@code app/data}, mirroring how the COBOL job runs over DALYTRAN/XREF/ACCT/TCATBAL.
 */
class SampleDataIntegrationTest {

    private static final ProcessingTimestampProvider FIXED_TS = () -> "2022-06-10-19.27.53.000000";

    @Test
    void accountRecordRoundTripsThroughCodec() throws IOException {
        List<String> lines = Files.readAllLines(SampleData.file("acctdata.txt"), StandardCharsets.UTF_8);
        String line = lines.get(0);
        AccountRecord account = AccountRecord.parse(line);
        // Real record decodes to 194.00 current balance and re-encodes identically.
        assertEquals(new BigDecimal("194.00"), account.getCurrentBalance());
        assertEquals(line, account.toRecord());
    }

    @Test
    void firstDailyTransactionAmountParsesToExpectedValue() {
        List<DailyTransactionRecord> txns = RecordFiles.readDailyTransactions(SampleData.file("dailytran.txt"));
        assertEquals(300, txns.size());
        assertEquals(new BigDecimal("504.77"), txns.get(0).getAmount());
    }

    @Test
    void runsWholeSampleDatasetAndAccountsForEveryRecord() {
        CardXrefRepository xref = RecordFiles.loadCardXref(SampleData.file("cardxref.txt"));
        AccountRepository accounts = RecordFiles.loadAccounts(SampleData.file("acctdata.txt"));
        TranCatBalRepository tranCatBal = RecordFiles.loadTranCatBalances(SampleData.file("tcatbal.txt"));
        List<DailyTransactionRecord> txns = RecordFiles.readDailyTransactions(SampleData.file("dailytran.txt"));

        TransactionPostingService service =
                new TransactionPostingService(xref, accounts, tranCatBal, FIXED_TS);
        BatchSummary summary = new DailyTransactionPostingJob(service).run(txns);

        assertEquals(300, summary.getTransactionCount());
        // Every transaction is either posted or rejected; posted records serialize to 350 bytes.
        assertEquals(summary.getTransactionCount(),
                summary.getPostedTransactions().size() + summary.getRejectCount());
        assertTrue(summary.getPostedTransactions().stream().allMatch(t -> t.toRecord().length() == 350));
        // Reject records are always the raw 350-byte transaction plus the 80-byte trailer.
        assertTrue(summary.getRejectRecords().stream().allMatch(r -> r.length() == 430));
    }
}
