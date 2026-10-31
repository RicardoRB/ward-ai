package dev.ward.infrastructure.finding;

import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.project.Project;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

/** Reads the diagnostics {@code swift format lint} prints on standard error. */
public final class SwiftFormatOutputFindings implements GateFindingSource {
  static final String GATE = "swift-format";
  // Sources/App/Cart.swift:4:1: error: [Indentation] unindent by 2 spaces
  private static final Pattern DIAGNOSTIC =
      Pattern.compile(
          "^(?<file>\\S.*?):(?<line>\\d+):(?<column>\\d+): (?<level>error|warning):"
              + " \\[(?<rule>[A-Za-z]+)\\] (?<message>.*)$");

  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public SwiftFormatOutputFindings() {}

  @Override
  public String gateId() {
    return GATE;
  }

  @Override
  public List<Finding> extract(
      final Project project, final GateResult result, final Instant gateStart) {
    return OutputText.lines(result).stream()
        .map(DIAGNOSTIC::matcher)
        .filter(Matcher::matches)
        .map(diagnostic -> finding(project, diagnostic))
        .toList();
  }

  private static Finding finding(final Project project, final Matcher diagnostic) {
    final String message = diagnostic.group("message").strip();
    return new Finding(
        GATE,
        GATE,
        diagnostic.group("rule"),
        "error".equals(diagnostic.group("level")) ? FindingSeverity.ERROR : FindingSeverity.WARNING,
        message.isEmpty() ? "Formatting problem." : message,
        ReportFiles.relativize(project, diagnostic.group("file")),
        SwiftCompilerOutputFindings.positive(diagnostic.group("line")),
        SwiftCompilerOutputFindings.positive(diagnostic.group("column")),
        null);
  }
}
