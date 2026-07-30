package com.carddemo.posttran.files;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.carddemo.posttran.Account;
import com.carddemo.posttran.DailyTransaction;
import com.carddemo.posttran.TranCatBalance;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class LayoutsTest {

    private static final Path DATA = Path.of("..", "..", "app", "data", "ASCII");

    @Test
    void decodesTrailingOverpunchSigns() {
        assertEquals(new BigDecimal("504.77"), Cobol.decimal("0000005047G", 0, 11, 2));
        assertEquals(new BigDecimal("-919.00"), Cobol.decimal("0000009190}", 0, 11, 2));
        assertEquals(new BigDecimal("0.00"), Cobol.decimal("0000000000{", 0, 11, 2));
        assertEquals(new BigDecimal("-70.77"), Cobol.decimal("0000000707P", 0, 11, 2));
        assertEquals(new BigDecimal("504.77"), Cobol.decimal("00000050477", 0, 11, 2));
    }

    @Test
    void encodesTrailingOverpunchSigns() {
        assertEquals("0000005047G", Cobol.putDecimal(new BigDecimal("504.77"), 11, 2));
        assertEquals("0000009190}", Cobol.putDecimal(new BigDecimal("-919.00"), 11, 2));
        assertEquals("0000000000{", Cobol.putDecimal(BigDecimal.ZERO, 11, 2));
        assertEquals("00000000000{", Cobol.putDecimal(BigDecimal.ZERO, 12, 2));
    }

    @Test
    void truncatesRatherThanRoundsIntoAV99Field() {
        assertEquals("0000000012C", Cobol.putDecimal(new BigDecimal("1.2319"), 11, 2));
        assertEquals("0000000012L", Cobol.putDecimal(new BigDecimal("-1.2319"), 11, 2));
    }

    @Test
    void dailyTransactionRecordsSurviveARoundTrip() throws Exception {
        for (String record : records("dailytran.txt", Layouts.DALYTRAN_LENGTH)) {
            DailyTransaction decoded = Layouts.dailyTransaction(record);
            assertEquals(record, Layouts.dailyTransaction(decoded));
        }
    }

    @Test
    void accountRecordsSurviveARoundTrip() throws Exception {
        for (String record : records("acctdata.txt", Layouts.ACCOUNT_LENGTH)) {
            Account decoded = Layouts.account(record);
            assertEquals(record, Layouts.account(decoded));
        }
    }

    @Test
    void tranCatBalanceRecordsSurviveARoundTripWithTheirFiller() throws Exception {
        for (String record : records("tcatbal.txt", Layouts.TRAN_CAT_BAL_LENGTH)) {
            TranCatBalance decoded = Layouts.tranCatBalance(record);
            assertEquals(record, Layouts.tranCatBalance(decoded, Layouts.tranCatBalanceFiller(record)));
        }
    }

    private static List<String> records(String name, int length) throws Exception {
        return Files.readAllLines(DATA.resolve(name), StandardCharsets.ISO_8859_1).stream()
                .map(line -> line.replace("\r", ""))
                .filter(line -> !line.isEmpty())
                .map(line -> line.length() >= length ? line.substring(0, length)
                        : line + " ".repeat(length - line.length()))
                .toList();
    }
}
