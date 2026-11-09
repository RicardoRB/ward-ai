package dev.ward.analysis.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.check.UndefinedNameCheck;
import dev.ward.analysis.domain.scope.ClassScopes;
import dev.ward.analysis.domain.scope.NoScopes;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.ScopeBuilder;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Checks that the members of a dependency never declare a bare name for the rules that read names.
 */
final class ScopeViewMembersTest {

  private static final int POS_0 = 0;
  private static final int POS_5 = 5;
  private static final int POS_6 = 6;
  private static final int POS_20 = 20;
  private static final int POS_21 = 21;
  private static final int POS_100 = 100;

  private static Span span(final int from, final int to) {
    return new Span(1, from + 1, 1, to + 1, from, to);
  }

  @Test
  void aMemberOfADependencyDoesNotDeclareABareName() {
    final Symbol method = new Symbol("helper", span(POS_5, POS_6), "method");
    final Symbol function = new Symbol("util", span(POS_5, POS_6), "function");
    final DependencyView dependencies =
        new DependencyView(
            List.of(new FileSummary("a.py", List.of(), List.of(), List.of(method, function))),
            List.of(),
            List.of());

    final ScopeView view = ScopeView.of(UnitScopes.EMPTY, dependencies, NoScopes.INSTANCE);

    assertThat(view.projectNames()).containsExactly("util");
  }

  @Test
  void aBareNameThatOnlyADependencyMethodDeclaresIsStillUndefinedForJava() {
    final ScopeBuilder builder = new ScopeBuilder(span(POS_0, POS_100), ClassScopes.VISIBLE);
    builder.reference("helper", span(POS_20, POS_21), ReferenceAccess.READ);
    final UnitScopes scopes = builder.build();
    final DependencyView dependencies =
        new DependencyView(
            List.of(
                new FileSummary(
                    "A.java",
                    List.of(),
                    List.of(),
                    List.of(new Symbol("helper", span(POS_5, POS_6), "method")))),
            List.of(),
            List.of());

    final List<RuleMatch> found =
        new UndefinedNameCheck("undefined '{value}'", Set.of())
            .matches(
                new SyntaxNode("file", span(POS_0, POS_100), Map.of(), List.of()),
                FlowView.NONE,
                ScopeView.of(scopes, dependencies, NoScopes.INSTANCE),
                new SourceUnit("m.java", AnalysisLanguage.JAVA, ""));

    assertThat(found).extracting(RuleMatch::message).containsExactly("undefined 'helper'");
  }
}
