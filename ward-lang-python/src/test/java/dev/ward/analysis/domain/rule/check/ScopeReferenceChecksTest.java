package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.NoScopes;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.Scope;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The checks on redefinitions, yields and returns outside functions, and star-import names. */
final class ScopeReferenceChecksTest {

  private static final SourceUnit UNIT = new SourceUnit("m.py", AnalysisLanguage.PYTHON, "");
  private static final int MODULE = 1;
  private static final int FUNCTION = 2;

  @Test
  void redefinitionReportsADefinitionThatRebindsAnUnusedOne() {
    final Binding first = ScopeFixtures.binding("f", BindingKind.FUNCTION, Nodes.at(0, 1), MODULE);
    final Binding second =
        ScopeFixtures.binding("f", BindingKind.FUNCTION, Nodes.at(50, 1), MODULE);

    assertThat(
            run(
                new RedefinitionCheck("redefined '{value}'"),
                ScopeFixtures.moduleScopes(false, first, second)))
        .extracting(RuleMatch::message)
        .containsExactly("redefined 'f'");
  }

  @Test
  void redefinitionAcceptsAUseBetweenTheDefinitionsAndAnOpenScope() {
    final Binding first = ScopeFixtures.binding("f", BindingKind.FUNCTION, Nodes.at(0, 1), MODULE);
    final Binding second =
        ScopeFixtures.binding("f", BindingKind.FUNCTION, Nodes.at(50, 1), MODULE);
    final UnitScopes used =
        new UnitScopes(
            List.of(
                new Scope(
                    0, ScopeKind.BUILTIN, Scope.NO_PARENT, ScopeFixtures.WHOLE, List.of(), false),
                new Scope(
                    MODULE,
                    ScopeKind.MODULE,
                    0,
                    ScopeFixtures.WHOLE,
                    List.of(first, second),
                    false)),
            List.of(ScopeFixtures.resolvedReference("f", MODULE, first)));

    assertThat(run(new RedefinitionCheck("m"), used)).isEmpty();
    assertThat(run(new RedefinitionCheck("m"), ScopeFixtures.moduleScopes(true, first, second)))
        .isEmpty();
  }

  @Test
  void outsideScopeReportsAYieldOutsideEveryFunction() {
    final SyntaxNode yield = Nodes.node("yield_expr", Nodes.leaf("yield"));
    final SyntaxNode root = Nodes.node("file_input", yield);

    assertThat(
            new OutsideScopeCheck(Set.of("yield_expr"), Set.of(ScopeKind.FUNCTION), "outside")
                .matches(root, null, view(ScopeFixtures.moduleScopes(false)), UNIT))
        .hasSize(1);
  }

  @Test
  void outsideScopeAcceptsAYieldInsideAFunction() {
    final SyntaxNode yield = Nodes.node("yield_expr", Nodes.leaf("yield"));
    final SyntaxNode root = Nodes.node("file_input", yield);
    final UnitScopes withFunction =
        new UnitScopes(
            List.of(
                new Scope(
                    0, ScopeKind.BUILTIN, Scope.NO_PARENT, ScopeFixtures.WHOLE, List.of(), false),
                new Scope(MODULE, ScopeKind.MODULE, 0, ScopeFixtures.WHOLE, List.of(), false),
                new Scope(
                    FUNCTION, ScopeKind.FUNCTION, MODULE, ScopeFixtures.WHOLE, List.of(), false)),
            List.of());

    assertThat(
            new OutsideScopeCheck(Set.of("yield_expr"), Set.of(ScopeKind.FUNCTION), "outside")
                .matches(root, null, view(withFunction), UNIT))
        .isEmpty();
  }

  @Test
  void starImportUsageReportsAnUnresolvedNameInAnOpenScope() {
    final NameReference unresolved =
        new NameReference(
            "sep", ScopeFixtures.READ, ReferenceAccess.READ, MODULE, Optional.empty(), false);
    final UnitScopes open =
        new UnitScopes(
            List.of(
                new Scope(
                    0, ScopeKind.BUILTIN, Scope.NO_PARENT, ScopeFixtures.WHOLE, List.of(), false),
                new Scope(MODULE, ScopeKind.MODULE, 0, ScopeFixtures.WHOLE, List.of(), true)),
            List.of(unresolved));

    assertThat(
            new StarImportUsageCheck("'{value}' may be from a star import")
                .matches(Nodes.leaf(""), null, view(open), UNIT))
        .extracting(RuleMatch::message)
        .containsExactly("'sep' may be from a star import");
  }

  private static List<RuleMatch> run(final RuleCheck check, final UnitScopes scopes) {
    return check.matches(Nodes.leaf(""), null, view(scopes), UNIT);
  }

  private static ScopeView view(final UnitScopes scopes) {
    return new ScopeView(scopes, Map.of(), Set.of(), NoScopes.INSTANCE);
  }
}
