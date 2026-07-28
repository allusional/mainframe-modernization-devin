package com.aws.carddemo.cbact04c.io;

import com.aws.carddemo.cbact04c.model.TransactionRecord;
import com.aws.carddemo.cbact04c.repository.TransactionWriter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes fixed-width Transaction records to a sequential output file (TRANSACT). */
public class FileTransactionWriter implements TransactionWriter, AutoCloseable {

    private final BufferedWriter writer;

    public FileTransactionWriter(Path output) {
        try {
            this.writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to open transaction output file: " + output, e);
        }
    }

    @Override
    public void write(TransactionRecord transaction) {
        try {
            writer.write(TransactionCodec.format(transaction));
            writer.newLine();
        } catch (IOException e) {
            throw new UncheckedIOException("Error writing transaction record", e);
        }
    }

    @Override
    public void close() {
        try {
            writer.close();
        } catch (IOException e) {
            throw new UncheckedIOException("Error closing transaction output file", e);
        }
    }
}
