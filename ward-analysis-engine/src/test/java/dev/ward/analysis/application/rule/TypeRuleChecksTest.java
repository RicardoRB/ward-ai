package dev.ward.analysis.application.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.analysis.domain.rule.check.CheckedRule;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A rule with capability TYPES receives the unit's facts from the batched pre-pass. */
class TypeRuleChecksTest {
  private static final Span CALL = new Span(1, 1, 1, 5, 0, 4);
  private static final SourceUnit UNIT = new SourceUnit("A.java", AnalysisLanguage.JAVA, "text");
  private static final RuleDefinition RULE =
      new RuleDefinition(
          "ward:java/typed",
          Set.of(AnalysisLanguage.JAVA),
          RuleCapability.TYPES,
          AnalysisSeverity.ERROR,
          "body");

  /** Reports each call whose type is known to be String, and asks for it. */
  private static final class StringCalls implements RuleCheck {
    @Override
    public FactNeed needs() {
      return new FactNeed(Set.of("call"), Set.of(FactKind.TYPE));
    }

    @Override
    public List<RuleMatch> matches(final SyntaxNode root) {
      return List.of();
    }

    @Override
    public List<RuleMatch> matches(
        final SyntaxNode root,
        final dev.ward.analysis.domain.rule.FlowView flow,
        final dev.ward.analysis.domain.rule.ScopeView scopes,
        final UnitTypeFacts types,
        final SourceUnit unit) {
      return types.typeOf(CALL).map(type -> new RuleMatch(CALL, type.name())).stream().toList();
    }
  }

  @Test
  void theCheckReadsTheFactsTheResolverAnsweredInOneRequest() {
    final List<Set<FactKind>> requests = new ArrayList<>();
    final TypeFacts resolver =
        (unit, spans, kinds) -> {
          requests.add(kinds);
          return new UnitTypeFacts(
              Map.of(
                  UnitTypeFacts.key(CALL),
                  new SpanFacts(
                      TypeRef.of("java.lang.String"),
                      null,
                      null,
                      List.of(),
                      List.of(),
                      List.of(),
                      List.of(),
                      null)));
        };

    final RuleRunResult result = run(new TypeFactsRun(Map.of(AnalysisLanguage.JAVA, resolver)));

    assertThat(result.findings())
        .singleElement()
        .satisfies(f -> assertThat(f.message()).isEqualTo("java.lang.String"));
    assertThat(requests).containsExactly(Set.of(FactKind.TYPE));
  }

  @Test
  void unknownFactsProduceNoFindings() {
    final TypeFacts resolver = (unit, spans, kinds) -> UnitTypeFacts.none();

    final RuleRunResult result = run(new TypeFactsRun(Map.of(AnalysisLanguage.JAVA, resolver)));

    assertThat(result.findings()).isEmpty();
    assertThat(result.notRun()).isEmpty();
  }

  @Test
  void withoutAResolverTheRuleIsNotRun() {
    final RuleRunResult result = run(new TypeFactsRun(Map.of()));

    assertThat(result.notRun()).hasSize(1);
    assertThat(result.findings()).isEmpty();
  }

  private static RuleRunResult run(final TypeFactsRun types) {
    final SyntaxNode root =
        new SyntaxNode(
            "file",
            new Span(1, 1, 1, 5, 0, 4),
            Map.of(),
            List.of(new SyntaxNode("call", CALL, Map.of(), List.of())));
    final List<FrontendSupport> frontends =
        types.withTypes(
            List.of(
                new FrontendSupport(
                    AnalysisLanguage.JAVA,
                    unit -> new ParseResult(root, List.of()),
                    Set.of(RuleCapability.SYNTAX))));
    return new RuleEngine(definition -> new CheckedRule(definition, new StringCalls()))
        .run(List.of(UNIT), frontends, List.of(RULE));
  }
}
