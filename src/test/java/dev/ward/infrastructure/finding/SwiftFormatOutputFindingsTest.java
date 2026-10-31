package dev.ward.infrastructure.finding;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SwiftFormatOutputFindingsTest {
  private static final String GATE = "swift-format";
  // swift-format-stderr.txt holds nine Indentation diagnostics in Sources/App/Cart.swift.
  private static final int CAPTURED = 9;
  private static final int FIRST_LINE = 2;

  @TempDir Path root;

  private final SwiftFormatOutputFindings source = new SwiftFormatOutputFindings();

  private List<Finding> extract(final String stderr) {
    return source.extract(
        FindingFixtures.xcode(root),
        FindingFixtures.failedWithStderr(GATE, stderr),
        FindingFixtures.justBefore());
  }

  @Test
  void mapsEachCapturedDiagnosticToItsRuleAndLocation() {
    final List<Finding> findings =
        extract(SwiftFixtures.read("swift/swift-format-stderr.txt", root));

    assertThat(findings).hasSize(CAPTURED);
    assertThat(findings)
        .first()
        .satisfies(
            finding -> {
              assertThat(finding.gateId()).isEqualTo(GATE);
              assertThat(finding.tool()).isEqualTo(GATE);
              assertThat(finding.ruleId()).isEqualTo("Indentation");
              assertThat(finding.severity()).isEqualTo(FindingSeverity.ERROR);
              assertThat(finding.message()).isEqualTo("unindent by 2 spaces");
              assertThat(finding.file()).isEqualTo("Sources/App/Cart.swift");
              assertThat(finding.line()).isEqualTo(FIRST_LINE);
              assertThat(finding.column()).isEqualTo(1);
            });
  }

  @Test
  void warningAndAbsolutePathAreMapped() {
    final List<Finding> findings =
        extract(
            root.resolve("Tests/AppTests/CartTests.swift")
                + ":3:5: warning: [OrderedImports]"
                + " sort import statements lexicographically\n");

    assertThat(findings)
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.severity()).isEqualTo(FindingSeverity.WARNING);
              assertThat(finding.file()).isEqualTo("Tests/AppTests/CartTests.swift");
            });
  }

  @Test
  void otherOutputGivesNoFindings() {
    assertThat(extract("error: Unknown option '--bogus'\nUsage: swift-format lint\n")).isEmpty();
  }
}
