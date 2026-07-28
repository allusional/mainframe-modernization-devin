package com.aws.carddemo.cbact04c.repository;

import com.aws.carddemo.cbact04c.model.CardXrefRecord;

import java.util.Optional;

/** Read the Card Cross-Reference file (XREFFILE) by account id (alternate index). */
public interface CardXrefRepository {

    Optional<CardXrefRecord> readByAccountId(long accountId);
}
