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
import javax.inject.Inject;

/** Reads TypeScript compiler diagnostics printed with {@code --pretty false}. */
public final class TscOutputFindings implements GateFindingSource {
  static final String GATE = "tsc";
  // src/app.ts(3,7): error TS2322: Type 'string' is not assignable to type 'number'.
  private static final Pattern DIAGNOSTIC =
      Pattern.compile(
          "^(?<file>\\S.*?)\\((?<line>\\d+),(?<column>\\d+)\\): (?<level>error|warning)"
              + " (?<code>TS\\d+): (?<message>.*)$");

  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public TscOutputFindings() {}

  @Override
  public String gateId() {
    return GATE;
  }

  @Override
  public List<Finding> extract(
      final Project project, final GateResult result, final Instant gateStart) {
    // Project references can report the same diagnostic twice, so identical findings are kept once.
    final Set<Finding> findings = new LinkedHashSet<>();
    for (final String line : OutputText.lines(result)) {
      final Matcher diagnostic = DIAGNOSTIC.matcher(line);
      if (diagnostic.matches()) {
        findings.add(finding(project, diagnostic));
      }
    }
    return List.copyOf(findings);
  }

  private static Finding finding(final Project project, final Matcher diagnostic) {
    final String message = diagnostic.group("message").strip();
    return new Finding(
        GATE,
        GATE,
        diagnostic.group("code"),
        "error".equals(diagnostic.group("level")) ? FindingSeverity.ERROR : FindingSeverity.WARNING,
        message.isEmpty() ? "TypeScript problem." : message,
        ReportFiles.relativize(project, diagnostic.group("file")),
        positive(diagnostic.group("line")),
        positive(diagnostic.group("column")),
        null);
  }

  private static Integer positive(final String value) {
    try {
      final int parsed = Integer.parseInt(value);
      return parsed > 0 ? parsed : null;
    } catch (NumberFormatException exception) {
      return null;
    }
  }
}
