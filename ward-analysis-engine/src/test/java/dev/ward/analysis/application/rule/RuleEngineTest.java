package dev.ward.analysis.application.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleExecutionError;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.RuleMatcher;
import dev.ward.analysis.domain.rule.RuleNotRun;
import dev.ward.analysis.domain.rule.RuleRunResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuleEngineTest {
  private static final String JAVA_LENGTH = "ward:java/method-length";
  private static final String JAVA_NAMES = "ward:java/field-names";
  private static final String JAVA_FLOW = "ward:java/data-flow";
  private static final String UNIT_A = "A.java";
  private static final String FAILING_UNIT = "Broken.java";
  private static final String THROWING_RULE = "ward:java/throws";
  private static final Span AT_2_5 = new Span(2, 5, 2, 9, 10, 14);

  private final List<String> parsed = new ArrayList<>();
  private final List<String> compiled = new ArrayList<>();

  @Test
  void runsRuleWhenFrontendProvidesItsCapability() {
    final RuleRunResult result =
        engine(Map.of(JAVA_LENGTH, root -> List.of(new RuleMatch(AT_2_5, "too long"))))
            .run(
                List.of(RuleEngineFixtures.unit(UNIT_A)),
                List.of(java(Set.of(RuleCapability.SYNTAX))),
                List.of(RuleEngineFixtures.rule(JAVA_LENGTH, RuleCapability.SYNTAX)));

    assertThat(result.findings())
        .containsExactly(
            new AnalysisFinding(
                JAVA_LENGTH,
                AnalysisSeverity.WARNING,
                "too long",
                UNIT_A,
                AT_2_5.startLine(),
                AT_2_5.startColumn(),
                null));
    assertThat(result.notRun()).isEmpty();
    assertThat(result.executionErrors()).isEmpty();
  }

  @Test
  void reportsRuleAsNotRunNamingTheMissingCapability() {
    final RuleRunResult result =
        engine(Map.of(JAVA_FLOW, root -> List.of(new RuleMatch(AT_2_5, "leak"))))
            .run(
                List.of(RuleEngineFixtures.unit(UNIT_A)),
                List.of(java(Set.of(RuleCapability.SYNTAX))),
                List.of(RuleEngineFixtures.rule(JAVA_FLOW, RuleCapability.DATAFLOW)));

    assertThat(result.notRun()).containsExactly(new RuleNotRun(JAVA_FLOW, RuleCapability.DATAFLOW));
    assertThat(result.findings()).isEmpty();
    assertThat(compiled).isEmpty();
  }

  @Test
  void reportsRuleAsNotRunWhenNoFrontendHandlesItsLanguage() {
    final RuleRunResult result =
        engine(Map.of(JAVA_LENGTH, root -> List.of()))
            .run(
                List.of(RuleEngineFixtures.unit(UNIT_A)),
                List.of(),
                List.of(RuleEngineFixtures.rule(JAVA_LENGTH, RuleCapability.SYNTAX)));

    assertThat(result.notRun()).containsExactly(new RuleNotRun(JAVA_LENGTH, RuleCapability.SYNTAX));
  }

  @Test
  void recordsParseFailureOnceAndAnalyzesTheOtherUnits() {
    final RuleRunResult result =
        engine(
                Map.of(
                    JAVA_LENGTH,
                    root -> List.of(new RuleMatch(AT_2_5, "too long")),
                    JAVA_NAMES,
                    root -> List.of(new RuleMatch(AT_2_5, "name"))))
            .run(
                List.of(
                    RuleEngineFixtures.unit(FAILING_UNIT), RuleEngineFixtures.unit("Good.java")),
                List.of(java(Set.of(RuleCapability.SYNTAX))),
                List.of(
                    RuleEngineFixtures.rule(JAVA_LENGTH, RuleCapability.SYNTAX),
                    RuleEngineFixtures.rule(JAVA_NAMES, RuleCapability.SYNTAX)));

    assertThat(result.executionErrors())
        .containsExactly(
            new RuleExecutionError(null, FAILING_UNIT, "the unit could not be parsed"));
    assertThat(result.findings()).extracting(AnalysisFinding::file).containsOnly("Good.java");
    assertThat(result.findings()).hasSize(2);
    assertThat(parsed).containsExactly(FAILING_UNIT, "Good.java");
  }

  @Test
  void recordsRuleFailureAndContinuesWithTheNextUnit() {
    final RuleRunResult result =
        engine(
                Map.of(
                    THROWING_RULE,
                    root -> {
                      throw new AnalysisFailedException("boom");
                    }))
            .run(
                List.of(RuleEngineFixtures.unit(UNIT_A), RuleEngineFixtures.unit("B.java")),
                List.of(java(Set.of(RuleCapability.SYNTAX))),
                List.of(RuleEngineFixtures.rule(THROWING_RULE, RuleCapability.SYNTAX)));

    assertThat(result.executionErrors())
        .containsExactly(
            new RuleExecutionError(THROWING_RULE, UNIT_A, "the rule could not run"),
            new RuleExecutionError(THROWING_RULE, "B.java", "the rule could not run"));
    assertThat(result.findings()).isEmpty();
  }

  @Test
  void recordsRuleThatCannotBeCompiledWithoutAPath() {
    final RuleRunResult result =
        engine(Map.of())
            .run(
                List.of(RuleEngineFixtures.unit(UNIT_A)),
                List.of(java(Set.of(RuleCapability.SYNTAX))),
                List.of(RuleEngineFixtures.rule("ward:java/missing", RuleCapability.SYNTAX)));

    assertThat(result.executionErrors())
        .containsExactly(
            new RuleExecutionError("ward:java/missing", null, "the rule could not be compiled"));
  }

  @Test
  void sortsFindingsByFileLineColumnAndRuleId() {
    final Span namesAt = new Span(1, 3, 1, 4, 0, 1);
    final Span lengthAt = new Span(2, 1, 2, 2, 5, 6);
    final RuleRunResult result =
        engine(
                Map.of(
                    JAVA_NAMES, root -> List.of(new RuleMatch(namesAt, "names")),
                    JAVA_LENGTH, root -> List.of(new RuleMatch(lengthAt, "length"))))
            .run(
                List.of(RuleEngineFixtures.unit("B.java"), RuleEngineFixtures.unit(UNIT_A)),
                List.of(java(Set.of(RuleCapability.SYNTAX))),
                List.of(
                    RuleEngineFixtures.rule(JAVA_LENGTH, RuleCapability.SYNTAX),
                    RuleEngineFixtures.rule(JAVA_NAMES, RuleCapability.SYNTAX)));

    assertThat(result.findings())
        .extracting(AnalysisFinding::file, AnalysisFinding::line, AnalysisFinding::ruleId)
        .containsExactly(
            tuple(UNIT_A, 1, JAVA_NAMES),
            tuple(UNIT_A, 2, JAVA_LENGTH),
            tuple("B.java", 1, JAVA_NAMES),
            tuple("B.java", 2, JAVA_LENGTH));
  }

  @Test
  void keepsFindingWithoutPositionAndOmitsItsLocation() {
    final RuleRunResult result =
        engine(Map.of(JAVA_LENGTH, root -> List.of(new RuleMatch(null, "file-level"))))
            .run(
                List.of(RuleEngineFixtures.unit(UNIT_A)),
                List.of(java(Set.of(RuleCapability.SYNTAX))),
                List.of(RuleEngineFixtures.rule(JAVA_LENGTH, RuleCapability.SYNTAX)));

    assertThat(result.findings())
        .containsExactly(
            new AnalysisFinding(
                JAVA_LENGTH, AnalysisSeverity.WARNING, "file-level", UNIT_A, null, null, null));
  }

  private RuleEngine engine(final Map<String, MatchFunction> behaviors) {
    return new RuleEngine(new FakeMatcher(behaviors));
  }

  private FrontendSupport java(final Set<RuleCapability> capabilities) {
    final LanguageFrontend frontend =
        unit -> {
          parsed.add(unit.path());
          if (FAILING_UNIT.equals(unit.path())) {
            throw new AnalysisFailedException("syntax error");
          }
          return new ParseResult(new SyntaxNode("file", AT_2_5, Map.of(), List.of()), List.of());
        };
    return new FrontendSupport(AnalysisLanguage.JAVA, frontend, capabilities);
  }

  /** Produces matches for one rule id. */
  @FunctionalInterface
  private interface MatchFunction {
    List<RuleMatch> apply(SyntaxNode root);
  }

  /** A matcher whose rules are the given functions; an unknown id fails to compile. */
  private final class FakeMatcher implements RuleMatcher {
    private final Map<String, MatchFunction> behaviors;

    FakeMatcher(final Map<String, MatchFunction> behaviors) {
      this.behaviors = behaviors;
    }

    @Override
    public CompiledRule compile(final RuleDefinition definition) {
      final MatchFunction behavior = behaviors.get(definition.id());
      if (behavior == null) {
        throw new AnalysisFailedException("no such rule");
      }
      compiled.add(definition.id());
      return new CompiledRule() {
        @Override
        public RuleDefinition definition() {
          return definition;
        }

        @Override
        public List<RuleMatch> matches(final SyntaxNode root) {
          return behavior.apply(root);
        }
      };
    }
  }
}
