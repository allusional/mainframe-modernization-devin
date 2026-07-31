package com.carddemo.cbtrn02c.io;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Key sequenced (KSDS-equivalent) file: fixed length records addressed by a
 * primary key that occupies the first {@code keyLength} bytes. Records are
 * held in key order, so writing the file back out reproduces what a sequential
 * read of the VSAM cluster would return.
 */
public final class IndexedFile {

    private final int recordLength;
    private final int keyLength;
    private final TreeMap<String, String> records = new TreeMap<>();

    private IndexedFile(int recordLength, int keyLength) {
        this.recordLength = recordLength;
        this.keyLength = keyLength;
    }

    /** OPEN INPUT / OPEN I-O of an existing cluster. */
    public static IndexedFile load(Path path, int recordLength, int keyLength) throws IOException {
        IndexedFile file = new IndexedFile(recordLength, keyLength);
        for (String record : RecordFile.read(path, recordLength)) {
            file.records.put(record.substring(0, keyLength), record);
        }
        return file;
    }

    /** OPEN OUTPUT: the cluster is (re)created empty. */
    public static IndexedFile empty(int recordLength, int keyLength) {
        return new IndexedFile(recordLength, keyLength);
    }

    /** READ ... INVALID KEY: returns null when the key is absent. */
    public String read(String key) {
        return records.get(key);
    }

    /** WRITE: fails on a duplicate key, like VSAM status 22. */
    public void write(String record) {
        requireLength(record);
        String key = record.substring(0, keyLength);
        if (records.containsKey(key)) {
            throw new IllegalStateException("duplicate key on WRITE: '" + key + "'");
        }
        records.put(key, record);
    }

    /** REWRITE: fails when the key is absent, like VSAM status 23. */
    public void rewrite(String record) {
        requireLength(record);
        String key = record.substring(0, keyLength);
        if (!records.containsKey(key)) {
            throw new IllegalStateException("record not found on REWRITE: '" + key + "'");
        }
        records.put(key, record);
    }

    public int size() {
        return records.size();
    }

    /** All records in ascending key order. */
    public List<String> sequential() {
        return new ArrayList<>(records.values());
    }

    /** CLOSE: persists the cluster as a flat file in key order. */
    public void save(Path path) throws IOException {
        RecordFile.write(path, sequential());
    }

    private void requireLength(String record) {
        if (record.length() != recordLength) {
            throw new IllegalArgumentException("record must be " + recordLength + " bytes, was " + record.length());
        }
    }

    public Map<String, String> asMap() {
        return java.util.Collections.unmodifiableMap(records);
    }
}
