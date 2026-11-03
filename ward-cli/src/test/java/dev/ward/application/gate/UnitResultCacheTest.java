package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisCache;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.RuleMatcher;
import dev.ward.analysis.domain.rule.RuleRunResult;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class UnitResultCacheTest {
  private static final Span AT = new Span(3, 5, 3, 9, 20, 24);
  private static final String LENGTH = "ward:java/method-length";
  private static final String CLEAN = "src/B.java";
  private static final String FLAGGED = "src/A.java";
  private static final List<SourceUnit> UNITS =
      List.of(
          new SourceUnit(FLAGGED, AnalysisLanguage.JAVA, "class A {}"),
          new SourceUnit(CLEAN, AnalysisLanguage.JAVA, "class B {}"));

  private final Map<String, String> entries = new HashMap<>();
  private final AnalysisCache cache =
      new AnalysisCache() {
        @Override
        public Optional<String> read(final String key) {
          return Optional.ofNullable(entries.get(key));
        }

        @Override
        public void write(final String key, final String value) {
          entries.put(key, value);
        }
      };
  private final AtomicInteger parses = new AtomicInteger();
  private final Set<String> broken = new HashSet<>();
  private final Set<String> flagged = new HashSet<>(Set.of(FLAGGED));
  private final Set<String> failing = new HashSet<>();
  private final LanguageFrontend frontend =
      unit -> {
        parses.incrementAndGet();
        if (broken.contains(unit.path())) {
          throw new AnalysisFailedException("syntax error");
        }
        return new ParseResult(new SyntaxNode(unit.path(), AT, Map.of(), List.of()), List.of());
      };
  private final RuleMatcher matcher =
      definition ->
          new CompiledRule() {
            @Override
            public RuleDefinition definition() {
              return definition;
            }

            @Override
            public List<RuleMatch> matches(final SyntaxNode root) {
              if (failing.contains(root.kind())) {
                throw new AnalysisFailedException("the rule broke");
              }
              return flagged.contains(root.kind())
                  ? List.of(new RuleMatch(AT, "too long"))
                  : List.of();
            }
          };
  private final List<FrontendSupport> frontends =
      List.of(new FrontendSupport(AnalysisLanguage.JAVA, frontend, Set.of(RuleCapability.SYNTAX)));
  private final UnitResultCache results =
      new UnitResultCache(new RuleEngine(matcher), AnalysisCacheFixtures.codec(), "0.6.0");
  private final RuleDefinition rule = rule("body");

  @Test
  void warmRunParsesNothingAndReportsTheColdResult() {
    final RuleRunResult cold =
        results.run(AnalysisCacheFixtures.NO_CACHE, UNITS, frontends, List.of(rule));
    results.run(cache, UNITS, frontends, List.of(rule));
    parses.set(0);

    final RuleRunResult warm = results.run(cache, UNITS, frontends, List.of(rule));

    assertThat(cold.findings()).hasSize(1);
    assertThat(warm.findings()).isEqualTo(cold.findings());
    assertThat(entries).hasSize(2);
    assertThat(parses.get()).isZero();
  }

  @Test
  void editedUnitIsAnalyzedAgainAndOthersAreReused() {
    results.run(cache, UNITS, frontends, List.of(rule));
    parses.set(0);
    final List<SourceUnit> edited =
        List.of(new SourceUnit(FLAGGED, AnalysisLanguage.JAVA, "class A { }"), UNITS.get(1));

    results.run(cache, edited, frontends, List.of(rule));

    assertThat(parses.get()).isEqualTo(1);
  }

  @Test
  void changedRuleBodyAnalyzesEveryUnitAgain() {
    results.run(cache, UNITS, frontends, List.of(rule));
    parses.set(0);

    results.run(cache, UNITS, frontends, List.of(rule("changed")));

    assertThat(parses.get()).isEqualTo(2);
  }

  @Test
  void runWithAParseFailureStoresNothing() {
    broken.add(CLEAN);

    final RuleRunResult run = results.run(cache, UNITS, frontends, List.of(rule));

    assertThat(run.executionErrors()).hasSize(1);
    assertThat(entries).isEmpty();
  }

  @Test
  void runWithARuleExecutionErrorStoresNothing() {
    failing.add(CLEAN);

    final RuleRunResult run = results.run(cache, UNITS, frontends, List.of(rule));

    assertThat(run.executionErrors()).hasSize(1);
    assertThat(entries).isEmpty();
  }

  @Test
  void cleanRunStoresEveryUnit() {
    results.run(cache, UNITS, frontends, List.of(rule));

    assertThat(entries).hasSize(2);
  }

  @Test
  void corruptEntriesAreAnalyzedAgainAndOverwritten() {
    final RuleRunResult cold = results.run(cache, UNITS, frontends, List.of(rule));
    entries.replaceAll((key, value) -> "{\"formatVersion\":");
    parses.set(0);

    final RuleRunResult rebuilt = results.run(cache, UNITS, frontends, List.of(rule));
    final int parsesAfterRebuild = parses.get();
    results.run(cache, UNITS, frontends, List.of(rule));

    assertThat(rebuilt.findings()).isEqualTo(cold.findings());
    assertThat(parsesAfterRebuild).isEqualTo(2);
    assertThat(parses.get()).isEqualTo(2);
  }

  private static RuleDefinition rule(final String body) {
    return new RuleDefinition(
        LENGTH, AnalysisLanguage.JAVA, RuleCapability.SYNTAX, AnalysisSeverity.ERROR, body);
  }
}
