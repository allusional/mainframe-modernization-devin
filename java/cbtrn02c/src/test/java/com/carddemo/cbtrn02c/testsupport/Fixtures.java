package com.carddemo.cbtrn02c.testsupport;

import java.nio.file.Files;
import java.nio.file.Path;

/** Locations of the CardDemo ASCII sample data and of the COBOL baseline produced by GnuCOBOL. */
public final class Fixtures {

    /** java/cbtrn02c, the Maven module directory (surefire runs with it as working directory). */
    private static final Path MODULE = Path.of("").toAbsolutePath();
    private static final Path REPO = MODULE.getParent().getParent();

    private Fixtures() {
    }

    public static Path dailyTran() {
        return ascii("dailytran.txt");
    }

    public static Path cardXref() {
        return ascii("cardxref.txt");
    }

    public static Path acctData() {
        return ascii("acctdata.txt");
    }

    public static Path tcatBal() {
        return ascii("tcatbal.txt");
    }

    /** The golden output files produced by cobol-baseline/run_baseline.sh. */
    public static Path cobolBaseline() {
        return MODULE.resolve("cobol-baseline/baseline");
    }

    public static boolean cobolBaselineExists() {
        return Files.isRegularFile(cobolBaseline().resolve("transact.dat"));
    }

    private static Path ascii(String name) {
        return REPO.resolve("app/data/ASCII").resolve(name);
    }
}
