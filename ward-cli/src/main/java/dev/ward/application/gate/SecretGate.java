package dev.ward.application.gate;

import dev.ward.analysis.application.rule.TextRules;
import dev.ward.analysis.domain.ir.AnalysisFindingOrder;
import dev.ward.analysis.domain.rule.ExcludeGlob;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleExecutionError;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.SecretsOptions;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.project.Project;
import dev.ward.domain.secrets.GitHistory;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * The {@code secrets} gate: runs the secret rules over the text files of the project, and over the
 * history when it is asked to. Every file it cannot scan is an execution error, never a pass.
 */
public final class SecretGate implements QualityGate {
  private static final Logger LOGGER = Logger.getLogger(SecretGate.class.getName());
  private static final String TOOL = "secrets";

  private final String name;
  private final RuleSetup setup;
  private final AnalysisPorts ports;
  private final GitHistory history;
  private final SecretsOptions options;
  private final List<String> focus;

  SecretGate(
      final GateConfiguration check,
      final RuleSetup setup,
      final AnalysisPorts ports,
      final GitHistory history,
      final List<String> focus) {
    this.name = check.name();
    this.setup = setup;
    this.ports = ports;
    this.history = history;
    this.options = check.secrets();
    this.focus = List.copyOf(focus);
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public GateResult execute(final Project project) {
    final RuleSet set = setup.resolve(project.root(), 0);
    final List<String> problems = new ArrayList<>(set.problems());
    final List<Pattern> excludedPaths = globs(problems);
    if (!problems.isEmpty()) {
      return AnalysisGateResults.configurationError(name, problems);
    }
    final List<RuleDefinition> rules =
        set.rules().stream().filter(rule -> rule.capability() == RuleCapability.TEXT).toList();
    final Predicate<String> excluded = SecretHistoryScan.excluding(excludedPaths);
    final TextRead read = ports.sources().readText(project.root(), focus, excluded);
    RuleRunResult run =
        new TextResultCache(ports.matcher(), ports.codec(), ports.engineVersion())
            .run(ports.caches().open(project.root()), read.files(), rules);
    run = merge(run, new RuleRunResult(List.of(), read.errors(), List.of()));
    if (options.scanHistory() && focus.isEmpty()) {
      run =
          merge(
              run,
              new SecretHistoryScan(history)
                  .scan(
                      project.root(),
                      options.historySince(),
                      new TextRules(ports.matcher(), rules),
                      excluded));
    }
    run = ExcludedFindings.drop(run, set);
    if (LOGGER.isLoggable(Level.FINE)) {
      LOGGER.fine(
          "event=secrets-run files="
              + read.files().size()
              + " rules="
              + rules.size()
              + " findings="
              + run.findings().size()
              + " errors="
              + run.executionErrors().size());
    }
    return AnalysisGateResults.result(name, TOOL, run, rules);
  }

  private List<Pattern> globs(final List<String> problems) {
    final List<Pattern> patterns = new ArrayList<>();
    for (final String glob : options.excludePaths()) {
      try {
        patterns.add(ExcludeGlob.compile(glob));
      } catch (final IllegalArgumentException exception) {
        problems.add(
            "ward.toml: [checks." + name + "] exclude-paths: invalid glob \"" + glob + "\"");
      }
    }
    return patterns;
  }

  private static RuleRunResult merge(final RuleRunResult first, final RuleRunResult second) {
    final List<dev.ward.analysis.domain.ir.AnalysisFinding> findings =
        new ArrayList<>(first.findings());
    findings.addAll(second.findings());
    findings.sort(AnalysisFindingOrder.ORDER);
    final List<RuleExecutionError> errors = new ArrayList<>(first.executionErrors());
    errors.addAll(second.executionErrors());
    return new RuleRunResult(findings, errors, first.notRun());
  }
}
