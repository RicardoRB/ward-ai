package dev.ward.application.gate;

import dev.ward.application.format.FormatFileOutcome;
import dev.ward.application.format.FormatFileOutcome.Verdict;
import dev.ward.application.format.FormatService;
import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import dev.ward.domain.gate.GateFindings;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The {@code ward-format} gate: reports one error per file that the native formatter would change,
 * at its first differing line. A file the formatter refuses, or that cannot be read, is an
 * execution error, never a pass.
 */
public final class FormatGate implements QualityGate {
  /** The rule id of every finding. */
  public static final String RULE = "ward-format";

  private static final String TOOL = "ward";

  private final String name;
  private final FormatService service;
  private final List<String> focus;

  /**
   * Creates the gate, which checks only the root-relative paths in {@code focus}, or every file
   * when it is empty.
   */
  public FormatGate(final String name, final FormatService service, final List<String> focus) {
    this.name = name;
    this.service = service;
    this.focus = List.copyOf(focus);
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public GateResult execute(final Project project) {
    try {
      return result(service.check(project.root(), focus));
    } catch (final WardException exception) {
      return GateResult.builder(name, GateStatus.EXECUTION_ERROR)
          .summary("format configuration error")
          .stderr(exception.getMessage())
          .errors(0)
          .warnings(0)
          .build();
    }
  }

  private GateResult result(final List<FormatFileOutcome> outcomes) {
    final List<Finding> findings =
        outcomes.stream()
            .filter(outcome -> outcome.verdict() == Verdict.NEEDS_FORMATTING)
            .map(this::finding)
            .toList();
    final List<FormatFileOutcome> broken =
        outcomes.stream()
            .filter(
                outcome ->
                    outcome.verdict() == Verdict.REFUSED || outcome.verdict() == Verdict.FAILED)
            .toList();
    return GateResult.builder(name, status(findings, broken))
        .summary(summary(findings.size(), broken.size()))
        .stderr(details(broken))
        .errors(findings.size())
        .warnings(0)
        .build()
        .withFindings(GateFindings.of(findings));
  }

  /** A refused or unreadable file is never a pass, so it outranks the findings. */
  private static GateStatus status(
      final List<Finding> findings, final List<FormatFileOutcome> broken) {
    if (!broken.isEmpty()) {
      return GateStatus.EXECUTION_ERROR;
    }
    return findings.isEmpty() ? GateStatus.PASSED : GateStatus.FAILED;
  }

  private Finding finding(final FormatFileOutcome outcome) {
    return new Finding(
        name,
        TOOL,
        RULE,
        FindingSeverity.ERROR,
        FormatService.NOT_FORMATTED,
        outcome.path(),
        outcome.line(),
        null,
        null,
        List.of());
  }

  private static String summary(final int unformatted, final int broken) {
    if (broken > 0) {
      return broken + " file(s) could not be formatted";
    }
    return unformatted == 0 ? "all files formatted" : unformatted + " file(s) not formatted";
  }

  private static String details(final List<FormatFileOutcome> broken) {
    return broken.stream()
        .map(outcome -> outcome.path() + ": " + outcome.reason())
        .collect(Collectors.joining("\n"));
  }
}
