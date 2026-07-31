package com.carddemo.cbtrn02c;

import com.carddemo.cbtrn02c.copybook.AccountRecord;
import com.carddemo.cbtrn02c.copybook.TranCatBalRecord;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the crafted "branches" scenario, which exercises every path of
 * 1500-VALIDATE-TRAN, 2700-UPDATE-TCATBAL and 2800-UPDATE-ACCOUNT-REC.
 */
class PostingBranchTest {

    private static ScenarioFixture fixture;
    private static Cbtrn02c.Result result;

    @BeforeAll
    static void runJob(@TempDir Path work) throws IOException {
        fixture = ScenarioFixture.prepare("branches", work);
        result = fixture.run();
    }

    @Test
    void countsAndReturnCodeFollowTheCobol() {
        assertEquals(10, result.transactionCount());
        assertEquals(5, result.rejectCount());
        assertEquals(4, result.returnCode(), "RETURN-CODE 4 whenever anything was rejected");
    }

    @Test
    void eachValidationBranchProducesItsOwnReasonCode() throws IOException {
        Map<String, String> rejects = fixture.rejects().stream().collect(Collectors.toMap(
                r -> r.substring(0, 16).trim(),
                r -> r.substring(350, 354) + "|" + r.substring(354, 430).trim(),
                (a, b) -> a));

        assertEquals("0100|INVALID CARD NUMBER FOUND", rejects.get("TRAN000000000003"));
        assertEquals("0101|ACCOUNT RECORD NOT FOUND", rejects.get("TRAN000000000004"));
        assertEquals("0102|OVERLIMIT TRANSACTION", rejects.get("TRAN000000000005"));
        assertEquals("0103|TRANSACTION RECEIVED AFTER ACCT EXPIRATION", rejects.get("TRAN000000000006"));
        // Over limit *and* expired: 1500-B runs both checks, the later one wins.
        assertEquals("0103|TRANSACTION RECEIVED AFTER ACCT EXPIRATION", rejects.get("TRAN000000000007"));
        assertEquals(5, rejects.size());
    }

    @Test
    void rejectRecordKeepsTheDailyTransactionBytesAndAnEightyByteTrailer() throws IOException {
        for (String reject : fixture.rejects()) {
            assertEquals(430, reject.length());
            assertEquals(80, reject.substring(350).length());
        }
    }

    @Test
    void acceptedTransactionsAreWrittenToTheTransactionFile() throws IOException {
        List<String> ids = fixture.transactions().stream().map(t -> t.substring(0, 16)).toList();
        assertEquals(List.of("TRAN000000000001", "TRAN000000000002", "TRAN000000000008",
                "TRAN000000000009", "TRAN000000000010"), ids);
    }

    @Test
    void aTransactionExactlyOnTheCreditLimitIsAccepted() throws IOException {
        // Account 3 has a 100.00 limit and no cycle activity; 100.01 is rejected
        // (tran 5) while 100.00 posts (tran 8), because 1500-B compares with >=.
        AccountRecord account = accountsById().get(3L);
        assertEquals(new BigDecimal("100.00"), account.currCycCredit);
        assertEquals(new BigDecimal("100.00"), account.currBal);
    }

    @Test
    void positiveAmountsGoToCycleCreditAndNegativeAmountsToCycleDebit() throws IOException {
        AccountRecord account = accountsById().get(1L);
        // 250.00 + 100.00 - 40.00 + 5.25 + 0.00
        assertEquals(new BigDecimal("315.25"), account.currBal);
        // 100.00 + 100.00 + 5.25 + 0.00 (the zero amount counts as a credit)
        assertEquals(new BigDecimal("205.25"), account.currCycCredit);
        // 50.00 + (-40.00)
        assertEquals(new BigDecimal("10.00"), account.currCycDebit);
    }

    @Test
    void categoryBalancesAreCreatedWhenMissingAndAccumulatedWhenPresent() throws IOException {
        Map<String, TranCatBalRecord> balances = fixture.categoryBalances().stream()
                .collect(Collectors.toMap(TranCatBalRecord::key, Function.identity()));

        // update path: 25.00 pre-existing + 100.00 (tran 1) + 5.25 (tran 9)
        assertEquals(new BigDecimal("130.25"), balances.get("00000000001010001").balance);
        // create paths
        assertEquals(new BigDecimal("-40.00"), balances.get("00000000001050002").balance);
        assertEquals(new BigDecimal("0.00"), balances.get("00000000001030004").balance);
        assertEquals(new BigDecimal("100.00"), balances.get("00000000003020003").balance);
        // untouched account, left exactly as loaded
        assertEquals(new BigDecimal("10.00"), balances.get("00000000002010001").balance);
        assertEquals(5, balances.size());
    }

    @Test
    void rejectedTransactionsLeaveTheirAccountUntouched() throws IOException {
        AccountRecord expired = accountsById().get(2L);
        assertEquals(new BigDecimal("500.00"), expired.currBal);
        assertEquals(new BigDecimal("0.00"), expired.currCycCredit);
        assertEquals(new BigDecimal("0.00"), expired.currCycDebit);
    }

    @Test
    void postedTransactionsCarryADb2FormatProcessingTimestamp() throws IOException {
        for (String transaction : fixture.transactions()) {
            String procTs = transaction.substring(304, 330);
            assertTrue(procTs.matches("\\d{4}-\\d{2}-\\d{2}-\\d{2}\\.\\d{2}\\.\\d{2}\\.\\d{6}"),
                    "TRAN-PROC-TS '" + procTs + "' is not in DB2 format");
            assertTrue(procTs.endsWith("0000"), "the low order microseconds are always zero filled");
        }
    }

    private static Map<Long, AccountRecord> accountsById() throws IOException {
        return fixture.accounts().stream().collect(Collectors.toMap(a -> a.acctId, Function.identity()));
    }
}
