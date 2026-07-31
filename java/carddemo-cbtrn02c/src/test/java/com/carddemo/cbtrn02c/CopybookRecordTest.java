package com.carddemo.cbtrn02c;

import com.carddemo.cbtrn02c.copybook.AccountRecord;
import com.carddemo.cbtrn02c.copybook.CardXrefRecord;
import com.carddemo.cbtrn02c.copybook.DalytranRecord;
import com.carddemo.cbtrn02c.copybook.TranCatBalRecord;
import com.carddemo.cbtrn02c.copybook.TranRecord;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Each copybook layout must deserialize and re-serialize sample data byte for byte. */
class CopybookRecordTest {

    private static final Path DATA = ScenarioFixture.MODULE.resolve("parity/data/branches");

    @Test
    void dalytranRecordMatchesCvtra06y() throws IOException {
        String record = record("dailytran.txt", 0);
        DalytranRecord r = DalytranRecord.parse(record);
        assertEquals("TRAN000000000001", r.id);
        assertEquals("01", r.typeCd);
        assertEquals(1L, r.catCd);
        assertEquals("POS TERM  ", r.source);
        assertEquals(new BigDecimal("100.00"), r.amt);
        assertEquals(123456789L, r.merchantId);
        assertEquals("4000000000000001", r.cardNum);
        assertEquals("2023-05-01 10:00:00.000000", r.origTs);
        assertEquals(record, r.toRecord());
        assertEquals(record, r.raw());
    }

    @Test
    void tranRecordMatchesCvtra05y() throws IOException {
        DalytranRecord source = DalytranRecord.parse(record("dailytran.txt", 1));
        TranRecord tran = TranRecord.parse(source.raw());
        assertEquals(new BigDecimal("-40.00"), tran.amt);
        assertEquals(TranRecord.LENGTH, tran.toRecord().length());
        assertEquals(source.raw(), tran.toRecord());
    }

    @Test
    void accountRecordMatchesCvact01y() throws IOException {
        String record = record("acctdata.txt", 0);
        AccountRecord r = AccountRecord.parse(record);
        assertEquals(1L, r.acctId);
        assertEquals("Y", r.activeStatus);
        assertEquals(new BigDecimal("250.00"), r.currBal);
        assertEquals(new BigDecimal("1000.00"), r.creditLimit);
        assertEquals(new BigDecimal("100.00"), r.currCycCredit);
        assertEquals(new BigDecimal("50.00"), r.currCycDebit);
        assertEquals("2099-12-31", r.expirationDate);
        assertEquals(record, r.toRecord());
    }

    @Test
    void cardXrefRecordMatchesCvact03y() throws IOException {
        String record = record("cardxref.txt", 0);
        CardXrefRecord r = CardXrefRecord.parse(record);
        assertEquals("4000000000000001", r.cardNum);
        assertEquals(1L, r.custId);
        assertEquals(1L, r.acctId);
        assertEquals(record, r.toRecord());
    }

    @Test
    void tranCatBalRecordMatchesCvtra01y() throws IOException {
        String record = record("tcatbal.txt", 0);
        TranCatBalRecord r = TranCatBalRecord.parse(record);
        assertEquals(1L, r.acctId);
        assertEquals("01", r.typeCd);
        assertEquals(1L, r.catCd);
        assertEquals(new BigDecimal("25.00"), r.balance);
        assertEquals("00000000001010001", r.key());
        assertEquals(record, r.toRecord());
    }

    @Test
    void initializeLeavesFillerUntouched() {
        TranCatBalRecord r = new TranCatBalRecord();
        r.assign("00000000009" + "07" + "0003" + "0000000123D" + "XXXXXXXXXXXXXXXXXXXXXX");
        r.initialize();
        assertEquals("XXXXXXXXXXXXXXXXXXXXXX", r.filler);
        assertEquals(BigDecimal.ZERO, r.balance);
        assertEquals(0L, r.acctId);
    }

    private static String record(String file, int index) throws IOException {
        List<String> lines = java.nio.file.Files.readAllLines(DATA.resolve(file));
        return lines.get(index);
    }
}
