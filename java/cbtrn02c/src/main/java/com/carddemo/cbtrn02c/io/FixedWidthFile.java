package com.carddemo.cbtrn02c.io;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reader/writer for the fixed length record files used by the batch: either raw records with no
 * delimiter (as written by a COBOL {@code ORGANIZATION SEQUENTIAL} file) or the newline delimited
 * fixtures in {@code app/data/ASCII}, whose trailing FILLER bytes are stripped.
 */
public final class FixedWidthFile {

    private FixedWidthFile() {
    }

    public static List<String> read(Path path, int recordLength) {
        String content;
        try {
            content = Files.readString(path, StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path, e);
        }
        return content.indexOf('\n') >= 0
                ? readDelimited(path, content, recordLength)
                : readUndelimited(path, content, recordLength);
    }

    public static void write(Path path, List<String> records, int recordLength) {
        StringBuilder sb = new StringBuilder(records.size() * recordLength);
        for (String record : records) {
            if (record.length() != recordLength) {
                throw new IllegalArgumentException(
                        "record length " + record.length() + " != " + recordLength + " for " + path);
            }
            sb.append(record);
        }
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            Files.writeString(path, sb, StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + path, e);
        }
    }

    private static List<String> readDelimited(Path path, String content, int recordLength) {
        List<String> records = new ArrayList<>();
        for (String line : content.split("\n", -1)) {
            String record = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
            if (record.isEmpty()) {
                continue;
            }
            if (record.length() > recordLength) {
                throw new IllegalArgumentException(
                        path + ": record longer than " + recordLength + ": " + record.length());
            }
            records.add(record + " ".repeat(recordLength - record.length()));
        }
        return records;
    }

    private static List<String> readUndelimited(Path path, String content, int recordLength) {
        if (content.length() % recordLength != 0) {
            throw new IllegalArgumentException(
                    path + ": length " + content.length() + " is not a multiple of " + recordLength);
        }
        List<String> records = new ArrayList<>(content.length() / recordLength);
        for (int i = 0; i < content.length(); i += recordLength) {
            records.add(content.substring(i, i + recordLength));
        }
        return records;
    }
}
