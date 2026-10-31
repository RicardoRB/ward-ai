package dev.ward.infrastructure.finding;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SwiftCompilerOutputFindingsTest {
  private static final int ERROR_COLUMN = 37;

  @TempDir Path root;

  private List<Finding> extract(final String gate, final String stderr) {
    return new SwiftCompilerOutputFindings(gate)
        .extract(
            FindingFixtures.xcode(root),
            FindingFixtures.failedWithStderr(gate, stderr),
            FindingFixtures.justBefore());
  }

  @Test
  void colouredErrorRepeatedInTheExcerptIsOneFinding() {
    final List<Finding> findings =
        extract("swift-build", SwiftFixtures.read("swift/swift-build-stderr.txt", root));

    assertThat(findings)
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.gateId()).isEqualTo("swift-build");
              assertThat(finding.ruleId()).isEqualTo("swiftc");
              assertThat(finding.severity()).isEqualTo(FindingSeverity.ERROR);
              assertThat(finding.message())
                  .isEqualTo(
                      "cannot convert return expression of type 'String' to return type 'Int'");
              assertThat(finding.file()).isEqualTo("Sources/App/Price.swift");
              assertThat(finding.line()).isEqualTo(1);
              assertThat(finding.column()).isEqualTo(ERROR_COLUMN);
            });
  }

  @Test
  void xcodebuildErrorsBelongToTheXcodeTestGate() {
    final String stdout =
        root.resolve("Shop/Cart.swift")
            + ":4:9: error: cannot find 'total' in scope\n** TEST FAILED **\n";

    assertThat(extract("xcodebuild-test", stdout))
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.gateId()).isEqualTo("xcodebuild-test");
              assertThat(finding.file()).isEqualTo("Shop/Cart.swift");
            });
  }

  @Test
  void warningsAndSummariesGiveNoFindings() {
    final String output =
        root.resolve("Shop/Cart.swift")
            + ":2:5: warning: variable 'x' was never used\nerror: Build failed\n";

    assertThat(extract("swift-build", output)).isEmpty();
  }
}
