package com.carddemo.posting.files;

import com.carddemo.interest.records.TranCatBalRecord;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The TCATBALF KSDS, opened I-O. Key = account id + transaction type + transaction category.
 * {@code 2700-UPDATE-TCATBAL} reads it, and either rewrites the bucket it found or creates a
 * new one initialised to zero.
 */
public class CategoryBalanceFile {

    private final Map<String, TranCatBalRecord> buckets = new TreeMap<>();

    public CategoryBalanceFile(Iterable<TranCatBalRecord> records) {
        for (TranCatBalRecord record : records) {
            buckets.put(record.key(), record);
        }
    }

    public Optional<TranCatBalRecord> read(String key) {
        return Optional.ofNullable(buckets.get(key));
    }

    /**
     * Adds the transaction amount to the bucket, creating it if the read came back
     * {@code INVALID KEY} (2700-A) rather than found (2700-B).
     *
     * @return true if the bucket had to be created, which is when the COBOL displays
     *         "TCATBAL record not found for key : ... Creating."
     */
    public boolean addToBalance(long accountId, String typeCode, String categoryCode, BigDecimal amount) {
        TranCatBalRecord existing = buckets.get(key(accountId, typeCode, categoryCode));
        boolean created = existing == null;
        TranCatBalRecord bucket = created
                ? TranCatBalRecord.initialize(accountId, typeCode, categoryCode)
                : existing;
        buckets.put(bucket.key(), bucket.withBalance(bucket.balance().add(amount)));
        return created;
    }

    public static String key(long accountId, String typeCode, String categoryCode) {
        return TranCatBalRecord.initialize(accountId, typeCode, categoryCode).key();
    }

    public Map<String, TranCatBalRecord> inKeyOrder() {
        return new LinkedHashMap<>(buckets);
    }
}
