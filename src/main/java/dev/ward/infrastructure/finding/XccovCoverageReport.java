package dev.ward.infrastructure.finding;

import dev.ward.domain.gate.CoverageReport;
import dev.ward.domain.gate.LineCoverage;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.json.JsonCodecException;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import org.apache.fory.json.annotation.JsonType;

/**
 * Reads production line coverage from the report {@code xcrun xccov view --report --json} prints,
 * leaving out test bundles.
 */
public final class XccovCoverageReport implements CoverageReport {
  private static final Logger LOGGER = Logger.getLogger(XccovCoverageReport.class.getName());
  private static final Pattern OBJECT_START = Pattern.compile("^\\{", Pattern.MULTILINE);
  // A test bundle's product is AppTests.xctest/AppTests, or .../Contents/MacOS/AppTests on macOS.
  private static final Pattern TEST_BUNDLE = Pattern.compile("\\.xctest(/|$)");
  private static final String RUN_TESTS_FIRST =
      " Run the xcodebuild-test check first, so .ward/reports/xcode/tests.xcresult exists.";

  private final JsonCodec codec;

  /** Creates the adapter that decodes xccov reports with {@code codec}. */
  @Inject
  public XccovCoverageReport(final JsonCodec codec) {
    this.codec = codec;
  }

  @Override
  public LineCoverage read(final Project project, final String stdout) {
    final Matcher start = OBJECT_START.matcher(Objects.toString(stdout, ""));
    if (!start.find()) {
      return LineCoverage.unusable("xccov printed no JSON report." + RUN_TESTS_FIRST);
    }
    final Report report;
    try {
      report = codec.fromJson(stdout.substring(start.start()), Report.class);
    } catch (JsonCodecException | IllegalArgumentException exception) {
      LOGGER.log(
          Level.INFO, "event=findings-parse-failed gate=" + SupportedQualityGates.SWIFT_COVERAGE);
      return LineCoverage.unusable("xccov's JSON report cannot be parsed.");
    }
    final List<Target> production =
        report == null
            ? List.of()
            : report.targetsOrEmpty().stream()
                .filter(target -> !TEST_BUNDLE.matcher(target.product()).find())
                .toList();
    final long covered = production.stream().mapToLong(Target::covered).sum();
    final long executable = production.stream().mapToLong(Target::executable).sum();
    if (executable == 0) {
      return LineCoverage.unusable(
          "xccov's report has no executable production lines. Enable code coverage for the"
              + " scheme's test action.");
    }
    return LineCoverage.of(covered, executable);
  }

  @JsonType
  static final class Report {
    List<Target> targets;

    List<Target> targetsOrEmpty() {
      return targets == null ? List.of() : targets.stream().filter(Objects::nonNull).toList();
    }
  }

  @JsonType
  static final class Target {
    String buildProductPath;
    Long coveredLines;
    Long executableLines;

    String product() {
      return Objects.toString(buildProductPath, "");
    }

    long covered() {
      return coveredLines == null ? 0 : coveredLines;
    }

    long executable() {
      return executableLines == null ? 0 : executableLines;
    }
  }
}
