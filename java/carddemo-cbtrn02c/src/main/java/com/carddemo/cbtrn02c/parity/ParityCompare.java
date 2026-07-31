package com.carddemo.cbtrn02c.parity;

import com.carddemo.cbtrn02c.copybook.AccountRecord;
import com.carddemo.cbtrn02c.copybook.TranCatBalRecord;
import com.carddemo.cbtrn02c.copybook.TranRecord;
import com.carddemo.cbtrn02c.io.RecordFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Compares the output datasets of the COBOL program with those of the Java
 * port, record by record and byte by byte.
 *
 * <p>The only tolerated difference is {@code TRAN-PROC-TS}: it is stamped from
 * the machine clock by {@code Z-GET-DB2-FORMAT-TIMESTAMP}, so the two runs can
 * never agree on it. That field is compared for format instead of value.
 */
public final class ParityCompare {

    /** Output datasets written by both implementations: name, record length. */
    public static final List<Dataset> DATASETS = List.of(
            new Dataset("TRANSACT.dat", TranRecord.LENGTH),
            new Dataset("ACCTDATA.dat", AccountRecord.LENGTH),
            new Dataset("TCATBAL.dat", TranCatBalRecord.LENGTH),
            new Dataset("DALYREJS.dat", 430));

    /** TRAN-PROC-TS within the 350 byte transaction record. */
    private static final int PROC_TS_OFFSET = 304;
    private static final int PROC_TS_LENGTH = 26;
    private static final Pattern DB2_TIMESTAMP =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}-\\d{2}\\.\\d{2}\\.\\d{2}\\.\\d{6}");

    public record Dataset(String fileName, int recordLength) {
    }

    /** One dataset comparison: how many records matched and what differed. */
    public record DatasetResult(String fileName, int cobolRecords, int javaRecords, List<String> differences) {
        public boolean matches() {
            return differences.isEmpty();
        }
    }

    private ParityCompare() {
    }

    public static List<DatasetResult> compare(Path cobolDir, Path javaDir) throws IOException {
        List<DatasetResult> results = new ArrayList<>();
        for (Dataset dataset : DATASETS) {
            results.add(compare(dataset, cobolDir.resolve(dataset.fileName()), javaDir.resolve(dataset.fileName())));
        }
        return results;
    }

    public static DatasetResult compare(Dataset dataset, Path cobolFile, Path javaFile) throws IOException {
        List<String> cobol = RecordFile.read(cobolFile, dataset.recordLength());
        List<String> java = RecordFile.read(javaFile, dataset.recordLength());
        List<String> differences = new ArrayList<>();
        if (cobol.size() != java.size()) {
            differences.add("record count differs: COBOL " + cobol.size() + " vs Java " + java.size());
        }
        boolean transactions = dataset.fileName().equals("TRANSACT.dat");
        for (int i = 0; i < Math.min(cobol.size(), java.size()); i++) {
            String c = cobol.get(i);
            String j = java.get(i);
            if (transactions) {
                checkProcTs(differences, i, c, j);
                c = maskProcTs(c);
                j = maskProcTs(j);
            }
            if (!c.equals(j)) {
                differences.add("record " + (i + 1) + " differs at byte " + (firstDifference(c, j) + 1)
                        + "\n  COBOL: " + c
                        + "\n  Java : " + j);
            }
        }
        return new DatasetResult(dataset.fileName(), cobol.size(), java.size(), differences);
    }

    private static void checkProcTs(List<String> differences, int index, String cobol, String java) {
        String cobolTs = procTs(cobol);
        String javaTs = procTs(java);
        if (!DB2_TIMESTAMP.matcher(cobolTs).matches() || !DB2_TIMESTAMP.matcher(javaTs).matches()) {
            differences.add("record " + (index + 1) + " TRAN-PROC-TS is not DB2 formatted:"
                    + "\n  COBOL: '" + cobolTs + "'\n  Java : '" + javaTs + "'");
        }
    }

    private static String procTs(String record) {
        return record.substring(PROC_TS_OFFSET, PROC_TS_OFFSET + PROC_TS_LENGTH);
    }

    private static String maskProcTs(String record) {
        return record.substring(0, PROC_TS_OFFSET)
                + "#".repeat(PROC_TS_LENGTH)
                + record.substring(PROC_TS_OFFSET + PROC_TS_LENGTH);
    }

    private static int firstDifference(String a, String b) {
        int limit = Math.min(a.length(), b.length());
        for (int i = 0; i < limit; i++) {
            if (a.charAt(i) != b.charAt(i)) {
                return i;
            }
        }
        return limit;
    }

    /** CLI: {@code ParityCompare <cobol-output-dir> <java-output-dir>}. */
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("usage: ParityCompare <cobol-output-dir> <java-output-dir>");
            System.exit(2);
        }
        boolean allMatch = true;
        for (DatasetResult result : compare(Path.of(args[0]), Path.of(args[1]))) {
            if (result.matches()) {
                System.out.printf("MATCH    %-14s %d records byte identical%n",
                        result.fileName(), result.cobolRecords());
            } else {
                allMatch = false;
                System.out.printf("MISMATCH %-14s COBOL %d records, Java %d records%n",
                        result.fileName(), result.cobolRecords(), result.javaRecords());
                result.differences().forEach(d -> System.out.println("         " + d));
            }
        }
        System.out.println(allMatch ? "PARITY OK" : "PARITY FAILED");
        System.exit(allMatch ? 0 : 1);
    }
}
