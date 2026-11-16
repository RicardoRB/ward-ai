package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.application.rule.TypeFactsRun;
import dev.ward.analysis.application.rule.UnusedDefinitionFindings;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.port.WorkLimit;
import dev.ward.analysis.domain.rule.DuplicateCodeRule;
import dev.ward.analysis.domain.rule.GraphRules;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.infrastructure.analysis.GateFrontends;
import dev.ward.infrastructure.rule.BundledRuleMatchers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The result of the rule engine must not depend on the number of analysis threads (#585): every
 * per-file rule runs over a sample of the violating rule fixtures of every language, and over two
 * files that do not parse, with one thread and with several. The findings, their order, the
 * execution errors and the rules not run must be equal.
 */
class ParallelEngineResultTest {
  /** Every n-th fixture is a unit, so the run stays a matter of seconds. */
  private static final int STRIDE = 12;

  private static final int THREADS = 8;
  private static final int PARALLEL_RUNS = 3;

  @Test
  void oneThreadAndSeveralThreadsGiveTheSameResultOverTheRuleFixtures() throws IOException {
    final List<SourceUnit> units = units();
    final List<RuleDefinition> rules = perFileRules();

    final RuleRunResult serial = run(units, rules, 1);

    assertThat(units).hasSizeGreaterThan(THREADS);
    assertThat(serial.findings()).isNotEmpty();
    assertThat(serial.executionErrors()).isNotEmpty();
    for (int i = 0; i < PARALLEL_RUNS; i++) {
      // Two threads is the smallest limit that analyzes file by file; one goes rule by rule.
      final RuleRunResult parallel = run(units, rules, i == 0 ? 2 : THREADS);
      assertThat(parallel.findings()).containsExactlyElementsOf(serial.findings());
      assertThat(parallel.executionErrors()).containsExactlyElementsOf(serial.executionErrors());
      assertThat(parallel.notRun()).containsExactlyElementsOf(serial.notRun());
    }
  }

  /** A run with fresh frontends, so no cache of an earlier run answers for this one. */
  private static RuleRunResult run(
      final List<SourceUnit> units,
      final List<RuleDefinition> rules,
      final int threads
  ) {
    return new RuleEngine(BundledRuleMatchers.create(), new WorkLimit(threads))
        .run(units, frontends(), rules);
  }

  /** The gate's frontends; every language answers type requests with no facts. */
  private static List<FrontendSupport> frontends() {
    final TypeFacts none = (unit, spans, kinds) -> UnitTypeFacts.none();
    final Map<AnalysisLanguage, TypeFacts> sources = new EnumMap<>(AnalysisLanguage.class);
    for (final AnalysisLanguage language : AnalysisLanguage.values()) {
      sources.put(language, none);
    }
    return new TypeFactsRun(sources).withTypes(GateFrontends.create(new AntlrLanguageFrontend()));
  }

  /** The rules the engine runs file by file, as the {@code ward} gate selects them. */
  private static List<RuleDefinition> perFileRules() {
    return CatalogRuleDefinitions.shipped().stream()
        .filter(rule -> rule.capability() != RuleCapability.TEXT)
        .filter(rule -> !GraphRules.isGraphRule(rule))
        .filter(rule -> !DuplicateCodeRule.isDuplicateRule(rule))
        .filter(rule -> !UnusedDefinitionFindings.isUnusedDefinitionRule(rule))
        .toList();
  }

  private static List<SourceUnit> units() throws IOException {
    final List<SourceUnit> units = new ArrayList<>();
    int seen = 0;
    for (final CatalogRow row : new RuleCatalogReader().load().rows()) {
      if (row.breaking() || row.isText()) {
        continue;
      }
      for (final AnalysisLanguage language : row.languages(CatalogStatus.IMPLEMENTED)) {
        final Path file = RuleFixtureSet.of(RuleFixtureSet.root(), row, language).violating("");
        if (!Files.isRegularFile(file)) {
          continue;
        }
        if (seen % STRIDE == 0) {
          units.add(unit(file, language));
        }
        seen++;
      }
    }
    units.add(new SourceUnit("broken/Broken.java", AnalysisLanguage.JAVA, "class {"));
    units.add(new SourceUnit("broken/broken.py", AnalysisLanguage.PYTHON, "def (:\n"));
    return units;
  }

  private static SourceUnit unit(final Path file, final AnalysisLanguage language)
      throws IOException {
    final Path root = RuleFixtureSet.root();
    return new SourceUnit(root.relativize(file).toString(), language, Files.readString(file));
  }
}
