package com.aws.carddemo.cbact04c.io;

import com.aws.carddemo.cbact04c.model.AccountRecord;
import com.aws.carddemo.cbact04c.model.CardXrefRecord;
import com.aws.carddemo.cbact04c.model.DisclosureGroupRecord;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Parses the real CardDemo ASCII sample records copied from app/data. */
class CodecTest {

    private static List<String> readSample(String name) {
        List<String> lines = new ArrayList<>();
        try (InputStream in = CodecTest.class.getResourceAsStream("/sample/" + name);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return lines;
    }

    @Test
    void parsesFirstAccountFromSample() {
        AccountRecord account = AccountCodec.parse(readSample("acctdata.txt").get(0));
        assertEquals(1L, account.getAccountId());
        assertEquals("Y", account.getActiveStatus());
        assertEquals(new BigDecimal("194.00"), account.getCurrentBalance());
        assertEquals(new BigDecimal("2020.00"), account.getCreditLimit());
        // Per copybook CVACT01Y, "A000000000" occupies ACCT-ADDR-ZIP (offset 102-112);
        // ACCT-GROUP-ID (offset 112-122) is blank in the sample data, so CBACT04C's
        // disclosure-group lookups resolve through the DEFAULT group.
        assertEquals("A000000000", account.getAddressZip());
        assertEquals("          ", account.getGroupId());
    }

    @Test
    void accountRecordRoundTrips() {
        String original = readSample("acctdata.txt").get(0);
        String rebuilt = AccountCodec.format(AccountCodec.parse(original));
        assertEquals(original, rebuilt);
    }

    @Test
    void parsesFirstDisclosureGroupFromSample() {
        DisclosureGroupRecord group = DisclosureGroupCodec.parse(readSample("discgrp.txt").get(0));
        assertEquals("A000000000", group.getAccountGroupId());
        assertEquals("01", group.getTranTypeCode());
        assertEquals(1, group.getTranCategoryCode());
        assertEquals(new BigDecimal("15.00"), group.getInterestRate());
    }

    @Test
    void parsesCardXrefForAccountOne() {
        CardXrefRecord match = readSample("cardxref.txt").stream()
                .map(CardXrefCodec::parse)
                .filter(x -> x.getAccountId() == 1L)
                .findFirst()
                .orElseThrow();
        assertEquals(16, match.getCardNumber().length());
    }
}
