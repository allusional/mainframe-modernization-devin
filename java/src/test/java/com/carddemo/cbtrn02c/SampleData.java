package com.carddemo.cbtrn02c;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Locates the CardDemo ASCII sample datasets ({@code app/data/ASCII}) relative to the
 * module, walking up the directory tree so the tests work regardless of the Maven
 * working directory.
 */
public final class SampleData {

    private SampleData() {
    }

    public static Path dir() {
        Path current = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && current != null; i++) {
            Path candidate = current.resolve("app/data/ASCII");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate app/data/ASCII from " + Path.of("").toAbsolutePath());
    }

    public static Path file(String name) {
        return dir().resolve(name);
    }
}
