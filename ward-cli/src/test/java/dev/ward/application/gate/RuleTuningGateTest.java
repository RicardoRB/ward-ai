package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Rule options, severity, paths and validation through {@code ward-rules.toml}. */
class RuleTuningGateTest {

  private static final String METHOD_LENGTH = "ward:java/method-length";
  private static final String TYPE_NAME = "ward:java/type-name";
  private static final String TABLE = "[rules.\"ward:java/method-length\"]\n";
  private static final int SEVENTY = 70;
  private static final int NINETY = 90;

  @TempDir Path dir;

  private FixtureProject fixture() {
    return new FixtureProject(dir);
  }

  @Test
  void anOptionChangesTheLimit() throws IOException {
    final FixtureProject f = fixture();
    f.source(FixtureProject.SRC_A, "class A", SEVENTY);
    f.config(FixtureProject.VERSION + TABLE + "max = 80\n");

    assertThat(FixtureProject.ids(f.run(List.of(), null, new HashMap<>())))
        .doesNotContain(METHOD_LENGTH);
  }

  @Test
  void severityCanBeLowered() throws IOException {
    final FixtureProject f = fixture();
    f.source(FixtureProject.SRC_A, "class A", NINETY);
    f.config(FixtureProject.VERSION + TABLE + "severity = \"warning\"\n");

    final GateResult result = f.run(List.of(), null, new HashMap<>());

    assertThat(result.findings().items())
        .filteredOn(finding -> METHOD_LENGTH.equals(finding.ruleId()))
        .singleElement()
        .satisfies(finding -> assertThat(finding.severity()).isEqualTo(FindingSeverity.WARNING));
  }

  @Test
  void pathsAreExcludedForOneRuleOnly() throws IOException {
    final FixtureProject f = fixture();
    f.source("src/generated/G.java", "class lower", NINETY);
    f.source(FixtureProject.SRC_A, "class A", NINETY);
    f.config(FixtureProject.VERSION + TABLE + "exclude-paths = [\"src/generated/**\"]\n");

    final List<Finding> findings = f.run(List.of(), null, new HashMap<>()).findings().items();

    assertThat(findings)
        .anySatisfy(
            finding -> {
              assertThat(finding.ruleId()).isEqualTo(METHOD_LENGTH);
              assertThat(finding.file()).isEqualTo(FixtureProject.SRC_A);
            })
        .noneSatisfy(
            finding ->
                assertThat(METHOD_LENGTH.equals(finding.ruleId()))
                    .isTrue()
                    .isEqualTo(finding.file().contains("generated")))
        .anySatisfy(
            finding -> {
              assertThat(finding.ruleId()).isEqualTo(TYPE_NAME);
              assertThat(finding.file()).isEqualTo("src/generated/G.java");
            });
  }

  @Test
  void aWrongTypeNamesTheTableAndTheKey() throws IOException {
    final FixtureProject f = fixture();
    f.config(FixtureProject.VERSION + TABLE + "max = \"sixty\"\n");

    final GateResult result = f.run(List.of(), null, new HashMap<>());

    assertThat(result.status()).isEqualTo(GateStatus.EXECUTION_ERROR);
    assertThat(result.stderr())
        .contains(
            "ward-rules.toml: [rules.\"ward:java/method-length\"] max: expected an integer >= 1,"
                + " got \"sixty\"");
  }

  @Test
  void everyProblemIsReported() throws IOException {
    final FixtureProject f = fixture();
    f.config(
        "version = 1\n[rules.\"ward:java/nope\"]\nmax = 1\n"
            + "[rules.\"ward:java/type-name\"]\npattern = \"[\"\n"
            + TABLE
            + "exclude-paths = [\"[a\"]\nseverity = \"fatal\"\ncolor = 1\n");

    final String stderr = f.run(List.of(), null, new HashMap<>()).stderr();

    assertThat(stderr)
        .contains("[rules.\"ward:java/nope\"]: unknown rule id")
        .contains("[rules.\"ward:java/type-name\"] pattern: expected a valid regular expression")
        .contains("exclude-paths: invalid glob")
        .contains("severity: expected")
        .contains("color: unknown option");
  }

  @Test
  void noSuppressionMarkerSilencesARule() throws IOException {
    final FixtureProject f = fixture();
    Files.createDirectories(f.root().resolve("src"));
    final String body = "    int a = 0; // NOSONAR # noqa\n".repeat(SEVENTY);
    Files.writeString(
        f.root().resolve(FixtureProject.SRC_A),
        "class A {\n  @SuppressWarnings(\"checkstyle:MethodLength\")\n  void run() {\n"
            + body
            + "  }\n}\n");
    f.config(FixtureProject.VERSION);

    assertThat(FixtureProject.ids(f.run(List.of(), null, new HashMap<>()))).contains(METHOD_LENGTH);
  }
}
