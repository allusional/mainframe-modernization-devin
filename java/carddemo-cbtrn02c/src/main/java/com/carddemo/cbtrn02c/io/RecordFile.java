package com.carddemo.cbtrn02c.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Fixed length record I/O, the flat-file equivalent of a QSAM dataset:
 * records are stored back to back with no delimiter, one byte per character.
 */
public final class RecordFile {

    private RecordFile() {
    }

    public static List<String> read(Path path, int recordLength) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length % recordLength != 0) {
            throw new IOException(path + " is " + bytes.length + " bytes, not a multiple of " + recordLength);
        }
        String content = new String(bytes, StandardCharsets.ISO_8859_1);
        List<String> records = new ArrayList<>(bytes.length / recordLength);
        for (int i = 0; i < content.length(); i += recordLength) {
            records.add(content.substring(i, i + recordLength));
        }
        return records;
    }

    public static void write(Path path, List<String> records) throws IOException {
        StringBuilder sb = new StringBuilder();
        records.forEach(sb::append);
        Files.write(path, sb.toString().getBytes(StandardCharsets.ISO_8859_1));
    }
}
