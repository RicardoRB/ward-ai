package dev.ward.analysis.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.ClassScopes;
import dev.ward.analysis.domain.scope.ImportOrigin;
import dev.ward.analysis.domain.scope.NoScopes;
import dev.ward.analysis.domain.scope.ScopeBuilder;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScopeViewTest {

  private static Span span(final int from, final int to) {
    return new Span(1, from + 1, 1, to + 1, from, to);
  }

  private static UnitScopes imports() {
    final ScopeBuilder builder = new ScopeBuilder(span(0, 100), ClassScopes.VISIBLE);
    builder.declareImport("c", span(20, 21), new ImportOrigin("a.b", "c", span(0, 30)), Set.of());
    builder.declareImport(
        "other", span(24, 29), new ImportOrigin("a.b", "e", span(0, 30)), Set.of());
    builder.declareImport(
        "ext", span(50, 53), new ImportOrigin("lib", "x", span(40, 60)), Set.of());
    return builder.build();
  }

  private static DependencyView view() {
    final Symbol declared = new Symbol("c", span(5, 6));
    return new DependencyView(
        List.of(new FileSummary("a/b.py", List.of(declared), List.of(), List.of(declared))),
        List.of(),
        List.of(
            new DependencyEdge("m.py", "a/b.py", new Reference("from:0:a.b:c", span(0, 30))),
            new DependencyEdge("m.py", "a/b.py", new Reference("from:0:a.b:e", span(0, 30)))));
  }

  @Test
  void anImportResolvesToTheFileAndTheSymbolItNames() {
    final UnitScopes scopes = imports();

    final ScopeView view = ScopeView.of(scopes, view(), NoScopes.INSTANCE);
    final Binding c = scopes.module().bindings().get(0);

    assertThat(view.imports().get(c).path()).isEqualTo("a/b.py");
    assertThat(view.imports().get(c).declaration()).contains(new Symbol("c", span(5, 6)));
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
    final Symbol nested = new Symbol("Outer.Inner", span(0, 1));
    final DependencyView dependencies =
        new DependencyView(
            List.of(new FileSummary("A.java", List.of(), List.of(), List.of(nested))),
            List.of(),
            List.of());

    final ScopeView view = ScopeView.of(UnitScopes.EMPTY, dependencies, NoScopes.INSTANCE);

    assertThat(view.projectNames()).containsExactly("Inner");
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
