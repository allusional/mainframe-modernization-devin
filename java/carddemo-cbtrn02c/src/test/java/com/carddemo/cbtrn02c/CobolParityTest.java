package com.carddemo.cbtrn02c;

import com.carddemo.cbtrn02c.parity.ParityCompare;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Functional parity gate: the Java port must reproduce the output datasets,
 * the counters and the return code of the COBOL program byte for byte.
 *
 * <p>The COBOL side is the baseline committed under {@code parity/golden},
 * produced from the unmodified {@code app/cbl/CBTRN02C.cbl} by
 * {@code parity/scripts/run_cobol.sh} (GnuCOBOL). Regenerate it with that
 * script whenever the COBOL program or the input datasets change.
 */
class CobolParityTest {

    @ParameterizedTest
    @ValueSource(strings = {"branches", "full"})
    void javaOutputMatchesTheCobolBaseline(String scenario, @TempDir Path work) throws IOException {
        ScenarioFixture fixture = ScenarioFixture.prepare(scenario, work);
        Cbtrn02c.Result result = fixture.run();
        Path golden = ScenarioFixture.goldenDirectory(scenario);

        String cobolStdout = Files.readString(golden.resolve("stdout.txt"));
        assertEquals(cobolStdout.strip(), String.join("\n", result.display()).strip(),
                "DISPLAY output must match the COBOL run line for line");
        assertEquals(Integer.parseInt(Files.readString(golden.resolve("rc.txt")).strip()), result.returnCode());

        List<ParityCompare.DatasetResult> comparisons = ParityCompare.compare(golden, fixture.directory);
        for (ParityCompare.DatasetResult comparison : comparisons) {
            assertTrue(comparison.matches(),
                    scenario + " / " + comparison.fileName() + " differs from the COBOL baseline:\n"
                            + String.join("\n", comparison.differences()));
            assertEquals(comparison.cobolRecords(), comparison.javaRecords());
        }
        assertTrue(comparisons.stream().anyMatch(c -> c.cobolRecords() > 0), "the baseline must not be empty");
    }
}
