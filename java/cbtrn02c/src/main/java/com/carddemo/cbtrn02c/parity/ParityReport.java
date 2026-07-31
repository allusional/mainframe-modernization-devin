package com.carddemo.cbtrn02c.parity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Prints the parity verdict between a COBOL baseline directory and a Java output directory.
 *
 * <pre>
 * java -cp target/classes com.carddemo.cbtrn02c.parity.ParityReport &lt;cobol-baseline-dir&gt; &lt;java-output-dir&gt;
 * </pre>
 *
 * Exits with 0 when every record of every output file matches, 1 otherwise.
 */
public final class ParityReport {

    private static final int MAX_DIFFERENCES_SHOWN = 20;

    private ParityReport() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("usage: ParityReport <cobol-baseline-dir> <java-output-dir>");
            System.exit(2);
        }
        Path baseline = Path.of(args[0]);
        Path javaOutput = Path.of(args[1]);

        System.out.println("COBOL baseline : " + baseline.toAbsolutePath());
        System.out.println("Java output    : " + javaOutput.toAbsolutePath());
        System.out.println();

        boolean allMatch = true;
        for (ParityChecker.FileComparison comparison : ParityChecker.compareAll(baseline, javaOutput)) {
            System.out.println(comparison.summary());
            List<ParityChecker.Difference> differences = comparison.differences();
            for (int i = 0; i < Math.min(differences.size(), MAX_DIFFERENCES_SHOWN); i++) {
                System.out.println("        " + differences.get(i));
            }
            if (differences.size() > MAX_DIFFERENCES_SHOWN) {
                System.out.println("        ... " + (differences.size() - MAX_DIFFERENCES_SHOWN) + " more");
            }
            allMatch &= comparison.matches();
        }

        Path returnCodeFile = baseline.resolve("return-code.txt");
        if (Files.exists(returnCodeFile)) {
            System.out.println();
            System.out.println("COBOL RETURN-CODE: " + Files.readString(returnCodeFile).trim());
        }

        System.out.println();
        System.out.println(allMatch
                ? "PARITY: PASS - all output records are identical to the COBOL baseline"
                : "PARITY: FAIL - see the differences above");
        System.exit(allMatch ? 0 : 1);
    }
}
