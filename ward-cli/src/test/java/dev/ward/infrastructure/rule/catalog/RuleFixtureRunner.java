package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.DuplicateCodeFindings;
import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.application.rule.TypeFactsRun;
import dev.ward.analysis.application.rule.UnusedDefinitionFindings;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.python.PythonFileSummarizer;
import dev.ward.analysis.domain.rule.DuplicateCodeRule;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.infrastructure.analysis.GateFrontends;
import dev.ward.infrastructure.rule.BundledRuleMatchers;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Runs one bundled rule over one source text through the real rule engine, with the rule's default
 * options and the frontends of the {@code ward} gate. One runner serves one test at a time, since
 * the frontend is not thread-safe.
 */
final class RuleFixtureRunner {

  private final List<FrontendSupport> frontends;

  /** Creates a runner whose frontends match the {@code ward} gate. */
  RuleFixtureRunner() {
    frontends = GateFrontends.create(new AntlrLanguageFrontend());
  }

  /**
   * Returns the findings of {@code rule} on {@code text}, after checking that the engine ran the
   * rule without an error. {@code typeFacts} are the facts of the fixture's {@code types.json}; a
   * rule with capability {@code TYPES} receives them for its language, as it would receive a native
   * resolver's (see {@link FixtureTypeFacts}).
   */
  List<AnalysisFinding> run(
      final RuleDefinition rule, final String path, final String text, final TypeFacts typeFacts) {
    return run(rule, path, text, typeFacts, FixtureProject.alone());
  }

  /**
   * Like {@link #run(RuleDefinition, String, String, TypeFacts)}, with the project's manifests and
   * the other source files of the project, which give cross-file facts but report nothing.
   */
  List<AnalysisFinding> run(
      final RuleDefinition rule,
      final String path,
      final String text,
      final TypeFacts typeFacts,
      final FixtureProject project) {
    final AnalysisLanguage language = rule.languages().iterator().next();
    if (DuplicateCodeRule.isDuplicateRule(rule)) {
      return DuplicateCodeFindings.findings(
          List.of(rule), List.of(new SourceUnit(path, language, text)), frontends);
    }
    if (UnusedDefinitionFindings.isUnusedDefinitionRule(rule)) {
      return unusedDefinitions(rule, new SourceUnit(path, language, text));
    }
    final RuleEngine engine = new RuleEngine(BundledRuleMatchers.create());
    final List<SourceUnit> units = List.of(new SourceUnit(path, language, text));
    final RuleRunResult result = engine.run(units, frontendsFor(rule, typeFacts), List.of(rule));
    assertThat(result.executionErrors()).as("execution errors of %s", rule.id()).isEmpty();
    assertThat(result.notRun()).as("rules not run: %s", rule.id()).isEmpty();
    final List<AnalysisFinding> all = new ArrayList<>(result.findings());
    final List<SourceUnit> projectUnits = new ArrayList<>(units);
    projectUnits.addAll(project.sources());
    all.addAll(
        engine.runProject(
            projectUnits, frontendsFor(rule, typeFacts), List.of(rule), project.manifests()));
    final Set<String> otherPaths =
        project.sources().stream().map(SourceUnit::path).collect(Collectors.toSet());
    return all.stream()
        .filter(finding -> finding.ruleId().equals(rule.id()))
        .filter(finding -> !otherPaths.contains(finding.file()))
        .toList();
  }

  /** A fixture is one file, so a definition is used when the file itself uses it. */
  private List<AnalysisFinding> unusedDefinitions(
      final RuleDefinition rule, final SourceUnit unit) {
    final FrontendSupport python =
        frontends.stream()
            .filter(support -> support.language() == unit.language())
            .findFirst()
            .orElseThrow();
    final FileSummary summary =
        new PythonFileSummarizer().summarize(unit, python.frontend().parse(unit).tree());
    return UnusedDefinitionFindings.findings(List.of(rule), Map.of(unit.path(), summary));
  }

  private List<FrontendSupport> frontendsFor(final RuleDefinition rule, final TypeFacts typeFacts) {
    if (rule.capability() != RuleCapability.TYPES) {
      return frontends;
    }
    final Map<AnalysisLanguage, TypeFacts> sources = new EnumMap<>(AnalysisLanguage.class);
    rule.languages().forEach(language -> sources.put(language, typeFacts));
    return new TypeFactsRun(sources).withTypes(frontends);
  }
}
