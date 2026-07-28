package com.aws.carddemo.cbact04c.io;

import com.aws.carddemo.cbact04c.model.TranCatBalRecord;
import com.aws.carddemo.cbact04c.repository.InMemoryAccountRepository;
import com.aws.carddemo.cbact04c.repository.InMemoryCardXrefRepository;
import com.aws.carddemo.cbact04c.repository.InMemoryDisclosureGroupRepository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Loads the ASCII CardDemo sample files into in-memory repositories (the "readers"). */
public final class CardDemoDataLoader {

    private CardDemoDataLoader() {
    }

    public static List<TranCatBalRecord> loadTranCatBal(Path path) {
        List<TranCatBalRecord> records = new ArrayList<>();
        for (String line : readLines(path)) {
            if (!line.isBlank()) {
                records.add(TranCatBalCodec.parse(line));
            }
        }
        return records;
    }

    public static InMemoryAccountRepository loadAccounts(Path path) {
        InMemoryAccountRepository repository = new InMemoryAccountRepository();
        for (String line : readLines(path)) {
            if (!line.isBlank()) {
                repository.put(AccountCodec.parse(line));
            }
        }
        return repository;
    }

    public static InMemoryCardXrefRepository loadCardXref(Path path) {
        InMemoryCardXrefRepository repository = new InMemoryCardXrefRepository();
        for (String line : readLines(path)) {
            if (!line.isBlank()) {
                repository.put(CardXrefCodec.parse(line));
            }
        }
        return repository;
    }

    public static InMemoryDisclosureGroupRepository loadDisclosureGroups(Path path) {
        InMemoryDisclosureGroupRepository repository = new InMemoryDisclosureGroupRepository();
        for (String line : readLines(path)) {
            if (!line.isBlank()) {
                repository.put(DisclosureGroupCodec.parse(line));
            }
        }
        return repository;
    }

    private static List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read file: " + path, e);
        }
    }
}
