package dev.ward.application.gate;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.application.rule.TypeSidecarRun;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.AnalysisCache;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleExecutionError;
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
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The {@code ward} gate: runs the selected analysis rules over the project's source units. Its
 * findings and execution errors become the gate result that the other gates report beside.
 */
public final class AnalysisGate implements QualityGate {
  private static final Logger LOGGER = Logger.getLogger(AnalysisGate.class.getName());
  private static final String TOOL = "ward";

  private final String name;
  private final List<RuleDefinition> rules;
  private final AnalysisPorts ports;
  private final String javaClasspath;
  private final List<String> focus;

  /** Creates the gate that runs {@code rules} through the adapters in {@code ports}. */
  public AnalysisGate(
      final String name, final List<RuleDefinition> rules, final AnalysisPorts ports) {
    this(name, rules, ports, null);
  }

  /**
   * Creates the gate that runs {@code rules} through the adapters in {@code ports}, resolving Java
   * bindings with the classpath file {@code javaClasspath}, relative to the project root, or
   * without them when it is {@code null}.
   */
  public AnalysisGate(
      final String name,
      final List<RuleDefinition> rules,
      final AnalysisPorts ports,
      final String javaClasspath) {
    this(name, rules, ports, javaClasspath, List.of());
  }

  /**
   * Creates the gate like the constructor without {@code focus}, and reports only the findings that
   * touch the root-relative changed paths in {@code focus}. An empty focus reports every finding.
   */
  public AnalysisGate(
      final String name,
      final List<RuleDefinition> rules,
      final AnalysisPorts ports,
      final String javaClasspath,
      final List<String> focus) {
    this.name = name;
    this.rules = List.copyOf(rules);
    this.ports = ports;
    this.javaClasspath = javaClasspath;
    this.focus = List.copyOf(focus);
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
    final UnitResultCache results =
        new UnitResultCache(
            new RuleEngine(ports.matcher()),
            ports.codec(),
            ports.engineVersion(),
            ports
                .dependencies()
                .forProject(project.root(), units, Optional.ofNullable(javaClasspath)));
    try (TypeSidecarRun types = new TypeSidecarRun(ports.typeSidecars().apply(project.root()))) {
      final UnitResultCache.CachedRun run =
          results.analyze(cache, units, types.withTypes(ports.frontends(), rules), rules);
      return result(focused(run));
    }
  }

  /**
   * Keeps the findings that touch the focus. Execution errors and rules that did not run stay, so
   * unanalyzed code is still never a pass, even when it lies outside the changed files.
   */
  private RuleRunResult focused(final UnitResultCache.CachedRun cached) {
    final RuleRunResult run = cached.result();
    if (focus.isEmpty()) {
      return run;
    }
    final List<AnalysisFinding> kept =
        new AnalysisFocus(focus, rules, cached.graph()).keep(run.findings());
    if (LOGGER.isLoggable(Level.FINE)) {
      LOGGER.fine(
          () ->
              "event=ward-focus focus="
                  + focus.size()
                  + " findings="
                  + run.findings().size()
                  + " reported="
                  + kept.size());
    }
    return new RuleRunResult(kept, run.executionErrors(), run.notRun());
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
        finding.helpUri(),
        List.of());
  }

  /** Unanalyzed code is never a pass: errors, then rules that cannot run, then the findings. */
  private static GateStatus status(final RuleRunResult run, final int errors) {
    if (!run.executionErrors().isEmpty()) {
      return GateStatus.EXECUTION_ERROR;
    }
    if (!run.notRunFailingGate().isEmpty()) {
      return GateStatus.UNAVAILABLE;
    }
    return errors > 0 ? GateStatus.FAILED : GateStatus.PASSED;
  }

  private String summary(final RuleRunResult run, final List<Finding> findings) {
    if (!run.executionErrors().isEmpty()) {
      return run.executionErrors().size() + " analysis error(s)";
    }
    if (!run.notRunFailingGate().isEmpty()) {
      return run.notRunFailingGate().size() + " rule(s) not run";
    }
    return rules.size() + " rule(s), " + findings.size() + " finding(s)";
  }

  /** One line per unit or rule that did not run, without source text. */
  private static String details(final RuleRunResult run) {
    final List<String> lines = new ArrayList<>();
    for (final RuleExecutionError error : run.executionErrors()) {
      lines.add(errorLine(error));
    }
    run.notRun()
        .forEach(
            skipped ->
                lines.add(skipped.ruleId() + " not run: its frontend lacks " + skipped.missing()));
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
