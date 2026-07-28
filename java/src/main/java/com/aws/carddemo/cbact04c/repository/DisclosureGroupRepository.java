package com.aws.carddemo.cbact04c.repository;

import com.aws.carddemo.cbact04c.model.DisclosureGroupRecord;

import java.util.Optional;

/** Read the Disclosure Group file (DISCGRP) by its composite key. */
public interface DisclosureGroupRepository {

    Optional<DisclosureGroupRecord> read(String accountGroupId, String tranTypeCode, int tranCategoryCode);
}
