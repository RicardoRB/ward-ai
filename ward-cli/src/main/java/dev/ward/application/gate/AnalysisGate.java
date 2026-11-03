package dev.ward.application.gate;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.AnalysisCache;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleExecutionError;
import dev.ward.analysis.domain.rule.RuleNotRun;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import dev.ward.domain.gate.GateFindings;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.project.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The {@code ward} gate: runs the selected analysis rules over the project's source units. Its
 * findings and execution errors become the gate result that the other gates report beside.
 */
public final class AnalysisGate implements QualityGate {
  private static final String TOOL = "ward";

  private final String name;
  private final List<RuleDefinition> rules;
  private final AnalysisPorts ports;
  private final UnitResultCache results;

  /** Creates the gate that runs {@code rules} through the adapters in {@code ports}. */
  public AnalysisGate(
      final String name, final List<RuleDefinition> rules, final AnalysisPorts ports) {
    this.name = name;
    this.rules = List.copyOf(rules);
    this.ports = ports;
    this.results =
        new UnitResultCache(
            new RuleEngine(ports.matcher()),
            ports.codec(),
            ports.engineVersion(),
            ports.dependencies());
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public GateResult execute(final Project project) {
    final Set<AnalysisLanguage> languages =
        ports.frontends().stream()
            .map(FrontendSupport::language)
            .collect(Collectors.toUnmodifiableSet());
    final List<SourceUnit> units = ports.sources().read(project.root(), languages);
    final AnalysisCache cache = ports.caches().open(project.root());
    return result(results.run(cache, units, ports.frontends(), rules));
  }

  private GateResult result(final RuleRunResult run) {
    final List<Finding> findings = run.findings().stream().map(this::finding).toList();
    final int errors = countOf(findings, FindingSeverity.ERROR);
    final int warnings = countOf(findings, FindingSeverity.WARNING);
    return GateResult.builder(name, status(run, errors))
        .summary(summary(run, findings))
        .stderr(details(run))
        .errors(errors)
        .warnings(warnings)
        .build()
        .withFindings(GateFindings.of(findings));
  }

  private Finding finding(final AnalysisFinding finding) {
    return new Finding(
        name,
        TOOL,
        finding.ruleId(),
        FindingSeverity.valueOf(finding.severity().name()),
        finding.message(),
        finding.file(),
        finding.line(),
        finding.column(),
        finding.helpUri());
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

  private String summary(final RuleRunResult run, final List<Finding> findings) {
    if (!run.executionErrors().isEmpty()) {
      return run.executionErrors().size() + " analysis error(s)";
    }
    if (!run.notRun().isEmpty()) {
      return run.notRun().size() + " rule(s) not run";
    }
    return rules.size() + " rule(s), " + findings.size() + " finding(s)";
  }

  /** One line per unit or rule that did not run, without source text. */
  private static String details(final RuleRunResult run) {
    final List<String> lines = new ArrayList<>();
    for (final RuleExecutionError error : run.executionErrors()) {
      lines.add(errorLine(error));
    }
    for (final RuleNotRun skipped : run.notRun()) {
      lines.add(skipped.ruleId() + " not run: its frontend lacks " + skipped.missing());
    }
    return String.join("\n", lines);
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
