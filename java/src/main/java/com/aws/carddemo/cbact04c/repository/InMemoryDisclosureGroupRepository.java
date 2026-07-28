package com.aws.carddemo.cbact04c.repository;

import com.aws.carddemo.cbact04c.model.DisclosureGroupRecord;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Map-backed {@link DisclosureGroupRepository} keyed by group id + type + category. */
public class InMemoryDisclosureGroupRepository implements DisclosureGroupRepository {

    private final Map<String, DisclosureGroupRecord> byKey = new HashMap<>();

    public void put(DisclosureGroupRecord record) {
        byKey.put(key(record.getAccountGroupId(), record.getTranTypeCode(), record.getTranCategoryCode()), record);
    }

    @Override
    public Optional<DisclosureGroupRecord> read(String accountGroupId, String tranTypeCode, int tranCategoryCode) {
        return Optional.ofNullable(byKey.get(key(accountGroupId, tranTypeCode, tranCategoryCode)));
    }

    /**
     * Build the composite key. The group id is a COBOL X(10) field, so it is
     * padded/truncated to 10 characters exactly as the mainframe key compares.
     */
    static String key(String accountGroupId, String tranTypeCode, int tranCategoryCode) {
        String group = accountGroupId == null ? "" : accountGroupId;
        if (group.length() < 10) {
            group = group + " ".repeat(10 - group.length());
        } else if (group.length() > 10) {
            group = group.substring(0, 10);
        }
        String type = tranTypeCode == null ? "  " : tranTypeCode;
        return group + "|" + type + "|" + String.format("%04d", tranCategoryCode);
    }
}
