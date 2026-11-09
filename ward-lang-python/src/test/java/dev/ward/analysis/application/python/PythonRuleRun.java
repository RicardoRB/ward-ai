package dev.ward.analysis.application.python;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatcher;
import dev.ward.analysis.domain.rule.RuleRunResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Runs one rule over one fixture file, with its per-file and its project-wide matches. */
final class PythonRuleRun {

  private PythonRuleRun() {}

  /**
   * The findings of {@code rule} on {@code file}, asserting that the engine ran the rule cleanly.
   */
  static List<AnalysisFinding> findings(
      final RuleMatcher matcher, final RuleDefinition rule, final Path file, final Path directory)
      throws IOException {
    final AnalysisLanguage language = rule.languages().iterator().next();
    final RuleEngine engine = new RuleEngine(matcher);
    final List<SourceUnit> units =
        List.of(
            new SourceUnit(
                file.getFileName().toString(),
                language,
                Files.readString(file, StandardCharsets.UTF_8)));
    final List<FrontendSupport> frontends =
        PythonFixtureFrontends.forRule(rule, language, directory);
    final RuleRunResult result = engine.run(units, frontends, List.of(rule));
    assertThat(result.executionErrors()).as("execution errors of %s", rule.id()).isEmpty();
    assertThat(result.notRun()).as("rules not run: %s", rule.id()).isEmpty();
    final List<AnalysisFinding> all = new ArrayList<>(result.findings());
    all.addAll(engine.runProject(units, frontends, List.of(rule)));
    return all.stream().filter(finding -> finding.ruleId().equals(rule.id())).toList();
  }
}
