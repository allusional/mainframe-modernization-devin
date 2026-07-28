package com.carddemo.interest.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes the line oriented ASCII renderings of the CardDemo datasets
 * (app/data/ASCII). Trailing carriage returns are tolerated because some of the shipped
 * files use CRLF and others use LF.
 */
public final class FixedWidthFiles {

    private FixedWidthFiles() {
    }

    public static List<String> readRecords(Path path) throws IOException {
        List<String> records = new ArrayList<>();
        for (String line : Files.readAllLines(path, StandardCharsets.ISO_8859_1)) {
            String record = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
            if (!record.isBlank()) {
                records.add(record);
            }
        }
        return records;
    }

    public static void writeRecords(Path path, List<String> records) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        StringBuilder content = new StringBuilder();
        for (String record : records) {
            content.append(record).append('\n');
        }
        Files.writeString(path, content.toString(), StandardCharsets.ISO_8859_1);
    }
}
