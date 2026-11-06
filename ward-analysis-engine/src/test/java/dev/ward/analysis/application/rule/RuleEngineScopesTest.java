package dev.ward.analysis.application.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.port.ScopeAnalysis;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.RuleMatcher;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ClassScopes;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.ScopeBuilder;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The {@code SCOPES} capability in the rule engine: gating, sharing and failures. */
class RuleEngineScopesTest {

  private static final Span AT = new Span(1, 1, 1, 2, 0, 1);

  private static final class CountingAnalysis implements ScopeAnalysis {
    private final boolean fails;
    private int calls;

    CountingAnalysis(final boolean fails) {
      this.fails = fails;
    }

    @Override
    public UnitScopes analyze(final SourceUnit unit, final SyntaxNode tree) {
      calls++;
      if (fails) {
        throw new AnalysisFailedException("boom");
      }
      final ScopeBuilder builder = new ScopeBuilder(AT, ClassScopes.VISIBLE);
      builder.reference("missing", AT, ReferenceAccess.READ);
      builder.declare("known", BindingKind.VARIABLE, AT, Set.of());
      return builder.build();
    }

    @Override
    public Set<String> builtins(final Set<String> environments) {
      return Set.of("known");
    }
  }

  /** Reports the unresolved names of the scopes the rule is given. */
  private static final class ScopeMatcher implements RuleMatcher {
    @Override
    public CompiledRule compile(final RuleDefinition definition) {
      return new CompiledRule() {
        @Override
        public RuleDefinition definition() {
          return definition;
        }

        @Override
        public List<RuleMatch> matches(final SyntaxNode root) {
          return List.of();
        }

        @Override
        public List<RuleMatch> matches(
            final SyntaxNode root,
            final dev.ward.analysis.domain.rule.DependencyView dependencies,
            final dev.ward.analysis.domain.rule.FlowView flow,
            final ScopeView scopes,
            final SourceUnit unit) {
          final List<RuleMatch> found = new ArrayList<>();
          scopes
              .scopes(Set.of())
              .unresolved()
              .forEach(reference -> found.add(new RuleMatch(AT, reference.name())));
          return found;
        }
      };
    }
  }

  private static RuleRunResult run(
      final CountingAnalysis analysis, final Set<RuleCapability> capabilities, final int rules) {
    final LanguageFrontend frontend =
        unit -> new ParseResult(new SyntaxNode("file", AT, Map.of(), List.of()), List.of());
    final FrontendSupport support =
        new FrontendSupport(
            AnalysisLanguage.JAVA, frontend, capabilities, (unit, tree) -> null, analysis);
    final List<RuleDefinition> definitions = new ArrayList<>();
    for (int index = 0; index < rules; index++) {
      definitions.add(RuleEngineFixtures.rule("ward:java/scope-" + index, RuleCapability.SCOPES));
    }
    return new RuleEngine(new ScopeMatcher())
        .run(List.of(RuleEngineFixtures.unit("src/X.java")), List.of(support), definitions);
  }

  @Test
  void ruleGetsTheUnresolvedNamesAfterTheBuiltinsOfItsEnvironment() {
    final CountingAnalysis analysis = new CountingAnalysis(false);

    final RuleRunResult result =
        run(analysis, Set.of(RuleCapability.SYNTAX, RuleCapability.SCOPES), 1);

    assertThat(result.findings()).extracting("message").containsExactly("missing");
  }

  @Test
  void severalRulesShareOneBuildOfTheUnit() {
    final CountingAnalysis analysis = new CountingAnalysis(false);

    final RuleRunResult result =
        run(analysis, Set.of(RuleCapability.SYNTAX, RuleCapability.SCOPES), 3);

    assertThat(analysis.calls).isEqualTo(1);
    assertThat(result.findings()).hasSize(3);
  }

  @Test
  void aRunWithoutScopeRulesBuildsNothing() {
    final CountingAnalysis analysis = new CountingAnalysis(false);
    final LanguageFrontend frontend =
        unit -> new ParseResult(new SyntaxNode("file", AT, Map.of(), List.of()), List.of());
    final FrontendSupport support =
        new FrontendSupport(
            AnalysisLanguage.JAVA,
            frontend,
            Set.of(RuleCapability.SYNTAX, RuleCapability.SCOPES),
            (unit, tree) -> null,
            analysis);

    new RuleEngine(new ScopeMatcher())
        .run(
            List.of(RuleEngineFixtures.unit("src/X.java")),
            List.of(support),
            List.of(RuleEngineFixtures.rule("ward:java/syntax", RuleCapability.SYNTAX)));

    assertThat(analysis.calls).isZero();
  }

  @Test
  void aFrontendWithoutTheCapabilityDoesNotRunTheRule() {
    final RuleRunResult result = run(new CountingAnalysis(false), Set.of(RuleCapability.SYNTAX), 1);

    assertThat(result.notRun()).extracting("missing").containsExactly(RuleCapability.SCOPES);
  }

  @Test
  void aFailedBuildIsAnExecutionErrorAndStopsNoOtherUnit() {
    final RuleRunResult result =
        run(new CountingAnalysis(true), Set.of(RuleCapability.SYNTAX, RuleCapability.SCOPES), 1);

    assertThat(result.executionErrors()).hasSize(1);
    assertThat(result.executionErrors().get(0).reason()).contains("scopes could not be built");
    assertThat(result.findings()).isEmpty();
  }
}
