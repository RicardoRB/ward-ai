package dev.ward.analysis.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.check.UndefinedNameCheck;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.ClassScopes;
import dev.ward.analysis.domain.scope.ImportOrigin;
import dev.ward.analysis.domain.scope.NoScopes;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.ScopeBuilder;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScopeViewTest {

  private static final int POS_0 = 0;
  private static final int POS_1 = 1;
  private static final int POS_5 = 5;
  private static final int POS_6 = 6;
  private static final int POS_20 = 20;
  private static final int POS_21 = 21;
  private static final int POS_24 = 24;
  private static final int POS_29 = 29;
  private static final int POS_30 = 30;
  private static final int POS_40 = 40;
  private static final int POS_50 = 50;
  private static final int POS_53 = 53;
  private static final int POS_60 = 60;
  private static final int POS_100 = 100;

  private static Span span(final int from, final int to) {
    return new Span(1, from + 1, 1, to + 1, from, to);
  }

  private static UnitScopes imports() {
    final ScopeBuilder builder = new ScopeBuilder(span(POS_0, POS_100), ClassScopes.VISIBLE);
    builder.declareImport(
        "c", span(POS_20, POS_21), new ImportOrigin("a.b", "c", span(POS_0, POS_30)), Set.of());
    builder.declareImport(
        "other", span(POS_24, POS_29), new ImportOrigin("a.b", "e", span(POS_0, POS_30)), Set.of());
    builder.declareImport(
        "ext", span(POS_50, POS_53), new ImportOrigin("lib", "x", span(POS_40, POS_60)), Set.of());
    return builder.build();
  }

  private static DependencyView view() {
    final Symbol declared = new Symbol("c", span(POS_5, POS_6));
    return new DependencyView(
        List.of(new FileSummary("a/b.py", List.of(declared), List.of(), List.of(declared))),
        List.of(),
        List.of(
            new DependencyEdge(
                "m.py", "a/b.py", new Reference("from:0:a.b:c", span(POS_0, POS_30))),
            new DependencyEdge(
                "m.py", "a/b.py", new Reference("from:0:a.b:e", span(POS_0, POS_30)))));
  }

  @Test
  void anImportResolvesToTheFileAndTheSymbolItNames() {
    final UnitScopes scopes = imports();

    final ScopeView view = ScopeView.of(scopes, view(), NoScopes.INSTANCE);
    final Binding c = scopes.module().bindings().get(0);

    assertThat(view.imports().get(c).path()).isEqualTo("a/b.py");
    assertThat(view.imports().get(c).declaration()).contains(new Symbol("c", span(POS_5, POS_6)));
  }

  @Test
  void anImportOfAMissingSymbolStillLeadsToTheFile() {
    final UnitScopes scopes = imports();

    final ScopeView view = ScopeView.of(scopes, view(), NoScopes.INSTANCE);

    assertThat(view.imports().get(scopes.module().bindings().get(1)).declaration()).isEmpty();
  }

  @Test
  void anImportFromOutsideTheProjectIsNotResolved() {
    final UnitScopes scopes = imports();

    final ScopeView view = ScopeView.of(scopes, view(), NoScopes.INSTANCE);

    assertThat(view.imports()).doesNotContainKey(scopes.module().bindings().get(2));
  }

  @Test
  void theNamesOfTheDependenciesAreCollectedBySimpleName() {
    final Symbol nested = new Symbol("Outer.Inner", span(POS_0, POS_1));
    final DependencyView dependencies =
        new DependencyView(
            List.of(new FileSummary("A.java", List.of(), List.of(), List.of(nested))),
            List.of(),
            List.of());

    final ScopeView view = ScopeView.of(UnitScopes.EMPTY, dependencies, NoScopes.INSTANCE);

    assertThat(view.projectNames()).containsExactly("Inner");
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

  @Test
  void theViewOfARuleThatReadsNoScopesIsEmpty() {
    assertThat(ScopeView.NONE.scopes().references()).isEmpty();
    assertThat(ScopeView.NONE.scopes(Set.of()).scopes().get(UnitScopes.BUILTIN_ID).bindings())
        .isEmpty();
  }

  @Test
  void onlySymbolAndScopeRulesReadDependencies() {
    assertThat(RuleCapability.SCOPES.readsDependencies()).isTrue();
    assertThat(RuleCapability.SYMBOLS.readsDependencies()).isTrue();
    assertThat(RuleCapability.SYNTAX.readsDependencies()).isFalse();
  }
}
