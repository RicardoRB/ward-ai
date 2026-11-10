package dev.ward.application.gate;

import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleExecutionError;
import dev.ward.analysis.domain.rule.RuleNotRun;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import dev.ward.domain.gate.GateFindings;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Turns a {@code ward} gate run into its gate result: status, summary, details and findings. */
final class AnalysisGateResults {
  private static final String TOOL = "ward";

  private AnalysisGateResults() {}

  /** The execution error of a rule set whose configuration has {@code problems}. */
  static GateResult configurationError(final String name, final List<String> problems) {
    return GateResult.builder(name, GateStatus.EXECUTION_ERROR)
        .summary(problems.size() + " rule configuration error(s)")
        .stderr(String.join("\n", problems))
        .build();
  }

  /** The gate result of {@code run}, the run of {@code rules} by the gate called {@code name}. */
  static GateResult result(
      final String name, final RuleRunResult run, final List<RuleDefinition> rules) {
    final List<Finding> findings =
        run.findings().stream()
            .map(finding -> AnalysisFindingMapper.toFinding(name, TOOL, finding))
            .toList();
    final int errors = countOf(findings, FindingSeverity.ERROR);
    final int warnings = countOf(findings, FindingSeverity.WARNING);
    return GateResult.builder(name, status(run, errors))
        .summary(summary(run, findings, rules))
        .stderr(details(run, rules))
        .errors(errors)
        .warnings(warnings)
        .build()
        .withFindings(GateFindings.of(findings));
  }

  /** Unanalyzed code is never a pass: errors, then rules that cannot run, then the findings. */
  private static GateStatus status(final RuleRunResult run, final int errors) {
    if (!run.executionErrors().isEmpty()) {
      return GateStatus.EXECUTION_ERROR;
    }
    if (!run.notRun().isEmpty()) {
      return GateStatus.UNAVAILABLE;
    }
    return errors > 0 ? GateStatus.FAILED : GateStatus.PASSED;
  }

  private static String summary(
      final RuleRunResult run, final List<Finding> findings, final List<RuleDefinition> rules) {
    if (!run.executionErrors().isEmpty()) {
      return run.executionErrors().size() + " analysis error(s)";
    }
    if (!run.notRun().isEmpty()) {
      return run.notRun().size() + " rule(s) not run";
    }
    return rules.size() + " rule(s), " + findings.size() + " finding(s)";
  }

  /** One line per unit or rule that did not run, without source text. */
  private static String details(final RuleRunResult run, final List<RuleDefinition> rules) {
    final List<String> lines = new ArrayList<>();
    for (final RuleExecutionError error : run.executionErrors()) {
      lines.add(errorLine(error));
    }
    run.notRun()
        .forEach(skipped -> lines.add(skipped.ruleId() + " not run: " + reason(skipped, rules)));
    return String.join("\n", lines);
  }

  private static String reason(final RuleNotRun skipped, final List<RuleDefinition> rules) {
    return skipped.missing() == RuleCapability.TYPES
        ? noTypeResolver(skipped.ruleId(), rules)
        : "its frontend lacks " + skipped.missing();
  }

  /** Names the languages of the rule that have no native type resolver registered yet. */
  private static String noTypeResolver(final String ruleId, final List<RuleDefinition> rules) {
    final String languages =
        rules.stream()
            .filter(rule -> rule.id().equals(ruleId))
            .flatMap(rule -> rule.languages().stream())
            .map(Enum::name)
            .sorted()
            .collect(Collectors.joining(", "));
    return "no native type resolver is registered for " + languages + " yet";
  }

  private static String errorLine(final RuleExecutionError error) {
    final String subject =
        Stream.of(error.path(), error.ruleId())
            .filter(Objects::nonNull)
            .collect(Collectors.joining(" "));
    return subject + ": " + error.reason();
  }

  private static int countOf(final List<Finding> findings, final FindingSeverity severity) {
    return Math.toIntExact(
        findings.stream().filter(finding -> finding.severity() == severity).count());
  }
}
