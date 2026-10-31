package dev.ward.infrastructure.finding;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SwiftLintJsonFindingsTest {
  private static final String GATE = "swiftlint";
  // swiftlint/report.json, captured from SwiftLint 0.65.1: a force_cast error and a line_length
  // warning in Sources/App/Lint.swift.
  private static final String REPORT = "swiftlint/report.json";
  private static final int CAST_LINE = 2;
  private static final int CAST_COLUMN = 24;
  private static final int LENGTH_LINE = 7;

  @TempDir Path root;

  private final SwiftLintJsonFindings source = new SwiftLintJsonFindings(new ForyJsonCodec());

  private List<Finding> extract(final String stdout) {
    return source.extract(
        FindingFixtures.xcode(root),
        FindingFixtures.failed(GATE, stdout),
        FindingFixtures.justBefore());
  }

  @Test
  void mapsAnErrorToItsRuleAndProjectRelativeLocation() {
    assertThat(extract(SwiftFixtures.read(REPORT, root)))
        .first()
        .satisfies(
            finding -> {
              assertThat(finding.gateId()).isEqualTo(GATE);
              assertThat(finding.tool()).isEqualTo(GATE);
              assertThat(finding.ruleId()).isEqualTo("force_cast");
              assertThat(finding.severity()).isEqualTo(FindingSeverity.ERROR);
              assertThat(finding.message()).isEqualTo("Force casts should be avoided");
              assertThat(finding.file()).isEqualTo("Sources/App/Lint.swift");
              assertThat(finding.line()).isEqualTo(CAST_LINE);
              assertThat(finding.column()).isEqualTo(CAST_COLUMN);
            });
  }

  @Test
  void warningKeepsItsSeverity() {
    assertThat(extract(SwiftFixtures.read(REPORT, root)))
        .last()
        .satisfies(
            finding -> {
              assertThat(finding.ruleId()).isEqualTo("line_length");
              assertThat(finding.severity()).isEqualTo(FindingSeverity.WARNING);
              assertThat(finding.line()).isEqualTo(LENGTH_LINE);
            });
  }

  @Test
  void violationWithoutACharacterHasNoColumn() {
    final String stdout =
        """
        [
          {
            "character" : null,
            "file" : "%s",
            "line" : 40,
            "reason" : "File should contain 400 lines or less",
            "rule_id" : "file_length",
            "severity" : "Warning",
            "type" : "File Length"
          }
        ]
        """
            .formatted(root.resolve("Sources/App/Cart.swift"));

    assertThat(extract(stdout)).singleElement().extracting(Finding::column).isNull();
  }

  @Test
  void swiftRunBuildLinesBeforeTheArrayAreIgnored() {
    final String stdout =
        "Building for debugging...\n[0/1] Planning build\n[1/1] Compiling plugin\n"
            + SwiftFixtures.read(REPORT, root);

    assertThat(extract(stdout)).hasSize(2);
  }

  @Test
  void emptyArrayOrTextOutputGivesNoFindings() {
    assertThat(extract("[\n\n]\n")).isEmpty();
    assertThat(extract("Sources/App/Cart.swift:12:9: error: Force Cast Violation\n")).isEmpty();
  }
}
