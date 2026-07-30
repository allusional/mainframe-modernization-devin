package com.carddemo.posttran;

import java.util.Optional;

/** XREF-FILE, keyed by card number (1500-A-LOOKUP-XREF). */
public interface XrefRepository {

    Optional<CardXref> findByCardNum(String cardNum);
}
