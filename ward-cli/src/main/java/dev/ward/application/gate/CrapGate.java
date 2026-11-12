package dev.ward.application.gate;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.domain.config.CrapOptions;
import dev.ward.domain.gate.FileLines;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.GateFindings;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.LineHits;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.project.Project;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * The {@code crap} gate: scores each function as {@code complexity^2 * (1 - coverage)^3 +
 * complexity}, with the complexity of the language's complexity rule and the coverage of the lines
 * that a coverage report marks inside the function. It runs no tests; it reads the reports that the
 * coverage check wrote.
 */
public final class CrapGate implements QualityGate {
  private static final Logger LOGGER = Logger.getLogger(CrapGate.class.getName());

  private final String name;
  private final CrapOptions options;
  private final AnalysisPorts analysis;
  private final CrapReports reports;

  /** Creates the gate called {@code name} with the options of its check. */
  public CrapGate(
      final String name,
      final CrapOptions options,
      final AnalysisPorts analysis,
      final CrapPorts ports) {
    this.name = name;
    this.options = options;
    this.analysis = analysis;
    this.reports = new CrapReports(ports, CrapReports.CRAP_FIRST);
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public GateResult execute(final Project project) {
    try {
      return run(project);
    } catch (CrapReportException e) {
      LOGGER.log(Level.INFO, () -> "event=crap-unusable gate=" + name);
      return GateResult.builder(name, GateStatus.EXECUTION_ERROR).summary(e.getMessage()).build();
    }
  }

  private GateResult run(final Project project) {
    final List<LineHits> hits = reports.read(project, options.reports(), name);
    final ComplexityRules complexity = ComplexityRules.load(analysis);
    final List<SourceUnit> units =
        analysis.sources().read(project.root(), complexity.frontends().keySet());
    final CoverageMatching matching =
        CoverageMatching.of(
            project.root(),
            hits,
            units.stream().map(SourceUnit::path).collect(Collectors.toUnmodifiableSet()));
    if (matching.lines().isEmpty()) {
      throw new CrapReportException(
          "The coverage reports name no project source file."
              + " The coverage check must run first.");
    }
    reports.requireFresh(project.root(), matching.namedBy());
    final List<Finding> findings = new ArrayList<>();
    int scored = 0;
    for (final SourceUnit unit : units) {
      final List<CrapFunctions.Scored> functions =
          score(
              unit,
              complexity.rules().get(unit.language()),
              complexity.frontends().get(unit.language()),
              matching);
      scored += functions.size();
      functions.stream()
          .filter(function -> function.score().score() > options.max())
          .forEach(function -> findings.add(CrapFindings.of(name, options.max(), unit, function)));
    }
    findings.sort(
        Comparator.comparing(Finding::file)
            .thenComparing(Finding::line)
            .thenComparing(Finding::column));
    return result(findings, scored);
  }

  private List<CrapFunctions.Scored> score(
      final SourceUnit unit,
      final CompiledRule rule,
      final FrontendSupport frontend,
      final CoverageMatching matching) {
    final Optional<FileLines> lines = Optional.ofNullable(matching.lines().get(unit.path()));
    try {
      final ParseResult parsed = frontend.frontend().parse(unit);
      if (parsed.diagnostics().isEmpty()) {
        return CrapFunctions.score(rule.measures(parsed.tree()), lines);
      }
    } catch (AnalysisFailedException e) {
      LOGGER.log(Level.FINE, "event=crap-parse-failed");
    }
    if (lines.isPresent()) {
      throw new CrapReportException(
          unit.path() + " is named by a coverage report but cannot be parsed.");
    }
    return List.of();
  }

  private GateResult result(final List<Finding> findings, final int scored) {
    LOGGER.log(
        Level.INFO,
        () ->
            "event=crap-scored gate="
                + name
                + " functions="
                + scored
                + " above="
                + findings.size()
                + " max="
                + options.max());
    final String summary =
        scored + " function(s) scored, " + findings.size() + " above " + options.max();
    return GateResult.builder(name, findings.isEmpty() ? GateStatus.PASSED : GateStatus.FAILED)
        .summary(summary)
        .errors(findings.size())
        .build()
        .withFindings(GateFindings.of(findings));
  }
}
