package dev.ward.application.gate;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.application.rule.TypeFactsRun;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.AnalysisCache;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * The {@code ward} gate: runs the selected analysis rules over the project's source units. Its
 * findings and execution errors become the gate result that the other gates report beside.
 */
public final class AnalysisGate implements QualityGate {
  private static final Logger LOGGER = Logger.getLogger(AnalysisGate.class.getName());

  private final String name;
  private final RuleSetup setup;
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
    this(name, (root, javaRelease) -> RuleSet.of(rules), ports, javaClasspath, focus);
  }

  /**
   * Creates the gate that chooses its rules with {@code setup} once the project root is known. A
   * set with problems ends the run with an execution error, and no rule runs.
   */
  public AnalysisGate(
      final String name,
      final RuleSetup setup,
      final AnalysisPorts ports,
      final String javaClasspath,
      final List<String> focus) {
    this.name = name;
    this.setup = setup;
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
    final RuleSet set = setup.resolve(project.root(), JavaRelease.of(project));
    if (!set.problems().isEmpty()) {
      return AnalysisGateResults.configurationError(name, set.problems());
    }
    final List<RuleDefinition> rules = set.rules();
    final Set<AnalysisLanguage> languages =
        ports.frontends().stream()
            .map(FrontendSupport::language)
            .collect(Collectors.toUnmodifiableSet());
    final List<SourceUnit> units = ports.sources().read(project.root(), languages);
    final AnalysisCache cache = ports.caches().open(project.root());
    final TypeFactsRun types =
        new TypeFactsRun(
            ports
                .nativeTypeFacts()
                .apply(
                    project.root(),
                    Optional.ofNullable(javaClasspath).map(project.root()::resolve)));
    final UnitResultCache results =
        new UnitResultCache(
            new RuleEngine(ports.matcher()),
            ports.codec(),
            ports.engineVersion(),
            ports
                .dependencies()
                .forProject(project.root(), units, Optional.ofNullable(javaClasspath)));
    final List<FrontendSupport> frontends = types.withTypes(ports.frontends());
    final Set<String> manifests =
        frontends.stream()
            .flatMap(frontend -> frontend.manifests().stream())
            .collect(Collectors.toUnmodifiableSet());
    final UnitResultCache.CachedRun run =
        results.analyze(
            cache, units, frontends, rules, ports.sources().manifests(project.root(), manifests));
    return AnalysisGateResults.result(name, focused(ExcludedFindings.drop(run, set), rules), rules);
  }

  /**
   * Keeps the findings that touch the focus. Execution errors and rules that did not run stay, so
   * unanalyzed code is still never a pass, even when it lies outside the changed files.
   */
  private RuleRunResult focused(
      final UnitResultCache.CachedRun cached, final List<RuleDefinition> rules) {
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
}
