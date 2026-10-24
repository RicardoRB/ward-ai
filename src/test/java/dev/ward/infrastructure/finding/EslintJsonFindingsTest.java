package dev.ward.infrastructure.finding;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EslintJsonFindingsTest {
  private static final String GATE = "eslint";
  private static final int ERROR_LINE = 4;
  private static final int ERROR_COLUMN = 7;

  @TempDir Path root;

  private final EslintJsonFindings source = new EslintJsonFindings(new ForyJsonCodec());

  private List<Finding> extract(final String stdout) {
    return source.extract(
        FindingFixtures.typescript(root),
        FindingFixtures.failed(GATE, stdout),
        FindingFixtures.justBefore());
  }

  private String output() {
    return """
        [{"filePath":"%s","messages":[
          {"ruleId":"no-unused-vars","severity":2,"message":"'x' is assigned a value but never used.",
           "line":4,"column":7,"nodeType":"Identifier"},
          {"ruleId":"prefer-const","severity":1,"message":"'y' is never reassigned.","line":9,
           "column":3}
        ],"errorCount":1,"warningCount":1,"source":"const x = 1;"},
         {"filePath":"%s","messages":[]}]
        """
        .formatted(root.resolve("src/app.ts"), root.resolve("src/clean.ts"));
  }

  @Test
  void mapsErrorsAndWarningsWithProjectRelativeLocations() {
    final List<Finding> findings = extract(output());

    assertThat(findings).hasSize(2);
    final Finding error = findings.getFirst();
    assertThat(error.gateId()).isEqualTo(GATE);
    assertThat(error.tool()).isEqualTo(GATE);
    assertThat(error.ruleId()).isEqualTo("no-unused-vars");
    assertThat(error.severity()).isEqualTo(FindingSeverity.ERROR);
    assertThat(error.message()).isEqualTo("'x' is assigned a value but never used.");
    assertThat(error.file()).isEqualTo("src/app.ts");
    assertThat(error.line()).isEqualTo(ERROR_LINE);
    assertThat(error.column()).isEqualTo(ERROR_COLUMN);
    assertThat(findings.get(1).ruleId()).isEqualTo("prefer-const");
    assertThat(findings.get(1).severity()).isEqualTo(FindingSeverity.WARNING);
  }

  @Test
  void parsingErrorWithoutRuleGetsTheCatchAllRule() {
    final String stdout =
        """
        [{"filePath":"%s","messages":[{"ruleId":null,"fatal":true,"severity":2,
          "message":"Parsing error: ';' expected.","line":2,"column":5}]}]
        """
            .formatted(root.resolve("src/broken.ts"));

    final List<Finding> findings = extract(stdout);

    assertThat(findings)
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.ruleId()).isEqualTo(GATE);
              assertThat(finding.severity()).isEqualTo(FindingSeverity.ERROR);
              assertThat(finding.file()).isEqualTo("src/broken.ts");
            });
  }

  @Test
  void skipsAPackageManagerPreambleBeforeTheArray() {
    final List<Finding> findings =
        extract("> web@1.0.0 lint\n> eslint . --format json\n\n" + output());

    assertThat(findings).hasSize(2);
  }

  @Test
  void outputThatIsNotJsonGivesNoFindings() {
    final String stylish =
        "/repo/src/app.ts\n  4:7  error  'x' is assigned a value but never used  no-unused-vars\n";

    assertThat(extract(stylish)).isEmpty();
    assertThat(extract("[not json")).isEmpty();
    assertThat(extract("")).isEmpty();
  }
}
