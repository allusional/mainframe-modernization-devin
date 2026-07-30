package com.carddemo.cbtrn02c.parity;

import com.carddemo.cbtrn02c.io.FixedWidthFile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Compares the output files of the Java port against the COBOL baseline record by record and
 * field by field, so that a difference is reported as a copybook field name and not as a byte
 * offset.
 */
public final class ParityChecker {

    private ParityChecker() {
    }

    /** One reported difference. */
    public record Difference(int recordNumber, String fieldName, String expected, String actual) {
        @Override
        public String toString() {
            return "record " + recordNumber + " field " + fieldName
                    + ": COBOL=[" + expected + "] JAVA=[" + actual + "]";
        }
    }

    /** Result of comparing one output file. */
    public record FileComparison(Layouts.RecordLayout layout, int expectedRecords, int actualRecords,
                                 int fieldsCompared, List<Difference> differences) {

        public boolean matches() {
            return expectedRecords == actualRecords && differences.isEmpty();
        }

        public String summary() {
            if (matches()) {
                return String.format("PASS  %-45s %5d records, %6d fields compared, 0 differences",
                        layout.fileName(), expectedRecords, fieldsCompared);
            }
            return String.format("FAIL  %-45s COBOL %d records vs JAVA %d records, %d field differences",
                    layout.fileName(), expectedRecords, actualRecords, differences.size());
        }
    }

    public static FileComparison compare(Layouts.RecordLayout layout, Path cobolBaselineDir, Path javaOutputDir) {
        List<String> expected = FixedWidthFile.read(cobolBaselineDir.resolve(layout.fileName()),
                layout.recordLength());
        List<String> actual = FixedWidthFile.read(javaOutputDir.resolve(layout.fileName()), layout.recordLength());
        List<Difference> differences = new ArrayList<>();
        int fieldsCompared = 0;
        int common = Math.min(expected.size(), actual.size());
        for (int i = 0; i < common; i++) {
            for (Layouts.Field field : layout.fields()) {
                String e = expected.get(i).substring(field.offset(), field.offset() + field.length());
                String a = actual.get(i).substring(field.offset(), field.offset() + field.length());
                fieldsCompared++;
                if (!e.equals(a)) {
                    differences.add(new Difference(i + 1, field.name(), e, a));
                }
            }
        }
        return new FileComparison(layout, expected.size(), actual.size(), fieldsCompared, differences);
    }

    public static List<FileComparison> compareAll(Path cobolBaselineDir, Path javaOutputDir) {
        List<FileComparison> comparisons = new ArrayList<>();
        for (Layouts.RecordLayout layout : Layouts.ALL) {
            comparisons.add(compare(layout, cobolBaselineDir, javaOutputDir));
        }
        return comparisons;
    }
}
