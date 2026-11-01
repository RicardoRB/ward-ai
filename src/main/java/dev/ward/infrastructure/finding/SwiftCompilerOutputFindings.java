package dev.ward.infrastructure.finding;

import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.project.Project;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the Swift compiler errors that {@code swift build} or {@code xcodebuild} print. */
final class SwiftCompilerOutputFindings implements GateFindingSource {
  static final String RULE = "swiftc";
  // /repo/Sources/App/Cart.swift:1:38: error: cannot convert return expression of type ...
  // The source excerpt repeats it as "`- error: ...", which does not start with a path.
  private static final Pattern ERROR =
      Pattern.compile(
          "^(?<file>/.+?\\.swift):(?<line>\\d+):(?<column>\\d+): error: (?<message>.*)$");

  private final String gate;

  /** Creates the source for gate {@code gate}, such as {@code swift-build}. */
  SwiftCompilerOutputFindings(final String gate) {
    this.gate = gate;
  }

  @Override
  public String gateId() {
    return gate;
  }

  @Override
  public List<Finding> extract(
      final Project project, final GateResult result, final Instant gateStart) {
    // The compiler can print one error for several targets, so identical findings are kept once.
    final Set<Finding> findings = new LinkedHashSet<>();
    for (final String line : OutputText.lines(result)) {
      final Matcher error = ERROR.matcher(line);
      if (error.matches()) {
        findings.add(finding(project, error));
      }
    }
    return List.copyOf(findings);
  }

  private Finding finding(final Project project, final Matcher error) {
    final String message = error.group("message").strip();
    return new Finding(
        gate,
        RULE,
        RULE,
        FindingSeverity.ERROR,
        message.isEmpty() ? "Compilation error." : message,
        ReportFiles.relativize(project, error.group("file")),
        positive(error.group("line")),
        positive(error.group("column")),
        null);
  }

  static Integer positive(final String value) {
    try {
      final int parsed = Integer.parseInt(value);
      return parsed > 0 ? parsed : null;
    } catch (NumberFormatException exception) {
      return null;
    }
  }
}
