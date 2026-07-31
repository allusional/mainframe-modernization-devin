package com.carddemo.cbtrn02c.copybook;

import com.carddemo.cbtrn02c.io.FixedWidthFile;
import com.carddemo.cbtrn02c.testsupport.Fixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every fixture record must survive parse + serialize unchanged: that proves the field offsets,
 * lengths and the sign/decimal handling of the copybook layouts.
 */
class RecordLayoutTest {

    @Test
    void accountRecordsRoundTrip() {
        List<String> records = FixedWidthFile.read(Fixtures.acctData(), AccountRecord.LENGTH);
        assertEquals(50, records.size());
        for (String raw : records) {
            assertEquals(raw, AccountRecord.parse(raw).serialize());
        }
    }

    @Test
    void cardXrefRecordsRoundTrip() {
        List<String> records = FixedWidthFile.read(Fixtures.cardXref(), CardXrefRecord.LENGTH);
        assertEquals(50, records.size());
        for (String raw : records) {
            assertEquals(raw, CardXrefRecord.parse(raw).serialize());
        }
    }

    @Test
    void tranCatBalRecordsRoundTrip() {
        List<String> records = FixedWidthFile.read(Fixtures.tcatBal(), TranCatBalRecord.LENGTH);
        assertEquals(50, records.size());
        for (String raw : records) {
            assertEquals(raw, TranCatBalRecord.parse(raw).serialize());
        }
    }

    @Test
    void dailyTransactionsParseWithTheExpectedFields() {
        List<String> records = FixedWidthFile.read(Fixtures.dailyTran(), DalyTranRecord.LENGTH);
        assertEquals(300, records.size());

        DalyTranRecord first = DalyTranRecord.parse(records.get(0));
        assertEquals("0000000000683580", first.id());
        assertEquals("01", first.typeCd());
        assertEquals("0001", first.catCd());
        assertEquals("POS TERM  ", first.source());
        assertEquals(new BigDecimal("504.77"), first.amount());
        assertEquals("800000000", first.merchantId());
        assertEquals("4859452612877065", first.cardNum());
        assertEquals("2022-06-10 19:27:53.000000", first.origTs());

        // a returned item carries a negative amount (overpunch '}' = -0)
        DalyTranRecord second = DalyTranRecord.parse(records.get(1));
        assertEquals(new BigDecimal("-919.00"), second.amount());
    }

    @Test
    void postedTransactionRecordsRoundTrip() {
        List<String> records = FixedWidthFile.read(Fixtures.dailyTran(), DalyTranRecord.LENGTH);
        for (String raw : records) {
            TranRecord posted = TranRecord.fromDalyTran(DalyTranRecord.parse(raw), "2022-07-11-12.00.00.000000");
            assertEquals(TranRecord.LENGTH, posted.serialize().length());
            assertEquals(posted.serialize(), TranRecord.parse(posted.serialize()).serialize());
        }
    }
}
