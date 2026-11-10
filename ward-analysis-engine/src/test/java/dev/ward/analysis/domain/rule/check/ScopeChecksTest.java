package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.port.ScopeAnalysis;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ClassScopes;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.ScopeBuilder;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScopeChecksTest {

  private static final int REFERENCES = 3;

  private static final int OFFSET_0 = 0;
  private static final int OFFSET_1 = 1;
  private static final int OFFSET_2 = 2;
  private static final int OFFSET_3 = 3;
  private static final int OFFSET_4 = 4;
  private static final int OFFSET_5 = 5;
  private static final int OFFSET_6 = 6;
  private static final int OFFSET_7 = 7;
  private static final int OFFSET_8 = 8;
  private static final int OFFSET_9 = 9;
  private static final int OFFSET_10 = 10;

  private static final SyntaxNode ROOT =
      new SyntaxNode("file", new Span(1, 1, 1, 1, 0, 0), Map.of(), List.of());

  private static Span at(final int offset) {
    return new Span(1, offset + 1, 1, offset + 2, offset, offset + 1);
  }

  private static ScopeView view(final UnitScopes scopes, final Set<String> builtins) {
    return new ScopeView(
        scopes,
        Map.of(),
        Set.of("Declared"),
        new ScopeAnalysis() {
          @Override
          public UnitScopes analyze(final SourceUnit unit, final SyntaxNode tree) {
            return scopes;
          }

          @Override
          public Set<String> builtins(final Set<String> environments) {
            return environments.isEmpty() ? builtins : Set.of("env");
          }
        });
  }

  private static List<RuleMatch> run(
      final RuleCheck check, final AnalysisLanguage language, final ScopeView view) {
    return check.matches(ROOT, FlowView.NONE, view, new SourceUnit("m", language, ""));
  }

  @Test
  void undefinedNamesAreTheUnresolvedOnesOutsideOpenScopes() {
    final ScopeBuilder builder = new ScopeBuilder(at(OFFSET_0), ClassScopes.VISIBLE);
    builder.reference("known", at(OFFSET_1), ReferenceAccess.READ);
    builder.reference("print", at(OFFSET_2), ReferenceAccess.READ);
    builder.reference("missing", at(OFFSET_3), ReferenceAccess.READ);
    final int open = builder.open(ScopeKind.FUNCTION, at(OFFSET_4));
    builder.markOpen(open);
    builder.reference("hidden", at(OFFSET_5), ReferenceAccess.READ);
    builder.close();
    builder.declare("known", BindingKind.VARIABLE, at(OFFSET_0), Set.of());

    final List<RuleMatch> found =
        run(
            new UndefinedNameCheck("undefined '{value}'", Set.of()),
            AnalysisLanguage.PYTHON,
            view(builder.build(), Set.of("print")));

    assertThat(found).extracting(RuleMatch::message).containsExactly("undefined 'missing'");
  }

  @Test
  void theEnvironmentsAreHandedToTheBuiltins() {
    final ScopeBuilder builder = new ScopeBuilder(at(OFFSET_0), ClassScopes.VISIBLE);
    builder.reference("env", at(OFFSET_1), ReferenceAccess.READ);
    final ScopeView view = view(builder.build(), Set.of());

    assertThat(
            run(new UndefinedNameCheck("m {value}", Set.of()), AnalysisLanguage.JAVASCRIPT, view))
        .hasSize(1);
    assertThat(
            run(
                new UndefinedNameCheck("m {value}", Set.of("node")),
                AnalysisLanguage.JAVASCRIPT,
                view))
        .isEmpty();
  }

  @Test
  void typesOfThePackageAreNotUndefinedInJavaKotlinAndSwift() {
    final ScopeBuilder builder = new ScopeBuilder(at(OFFSET_0), ClassScopes.VISIBLE);
    builder.reference("Upper", at(OFFSET_1), ReferenceAccess.READ);
    builder.reference("Declared", at(OFFSET_2), ReferenceAccess.READ);
    builder.reference("lower", at(OFFSET_3), ReferenceAccess.READ);
    final ScopeView view = view(builder.build(), Set.of());
    final UndefinedNameCheck check = new UndefinedNameCheck("{value}", Set.of());

    assertThat(run(check, AnalysisLanguage.JAVA, view))
        .extracting(RuleMatch::message)
        .containsExactly("lower");
    assertThat(run(check, AnalysisLanguage.PYTHON, view)).hasSize(REFERENCES);
  }

  @Test
  void unusedBindingsAreLocalsThatAreNeverRead() {
    final ScopeBuilder builder = new ScopeBuilder(at(OFFSET_0), ClassScopes.VISIBLE);
    builder.declare("moduleLevel", BindingKind.VARIABLE, at(OFFSET_1), Set.of());
    builder.open(ScopeKind.FUNCTION, at(OFFSET_2));
    builder.declare("param", BindingKind.PARAMETER, at(OFFSET_3), Set.of());
    builder.declare("unused", BindingKind.VARIABLE, at(OFFSET_4), Set.of());
    builder.reference("unused", at(OFFSET_4), ReferenceAccess.WRITE);
    builder.declare("used", BindingKind.VARIABLE, at(OFFSET_5), Set.of());
    builder.reference("used", at(OFFSET_6), ReferenceAccess.READ);
    builder.declare("_ignored", BindingKind.VARIABLE, at(OFFSET_7), Set.of());
    builder.declare("pair", BindingKind.VARIABLE, at(OFFSET_8), Set.of(BindingFlag.DESTRUCTURED));
    builder.declare("shared", BindingKind.VARIABLE, at(OFFSET_9), Set.of(BindingFlag.NONLOCAL));
    builder.declare("late", BindingKind.CONSTANT, at(OFFSET_10), Set.of());

    final List<RuleMatch> found =
        run(
            new UnusedBindingCheck("unused {value}"),
            AnalysisLanguage.PYTHON,
            view(builder.build(), Set.of()));

    assertThat(found)
        .extracting(RuleMatch::message)
        .containsExactly("unused unused", "unused late");
  }

  @Test
  void anOpenScopeHidesItsUnusedBindings() {
    final ScopeBuilder builder = new ScopeBuilder(at(OFFSET_0), ClassScopes.VISIBLE);
    final int function = builder.open(ScopeKind.FUNCTION, at(OFFSET_1));
    builder.markOpen(function);
    builder.declare("local", BindingKind.VARIABLE, at(OFFSET_2), Set.of());

    assertThat(
            run(
                new UnusedBindingCheck("m {value}"),
                AnalysisLanguage.PYTHON,
                view(builder.build(), Set.of())))
        .isEmpty();
  }

  @Test
  void theChecksNeedTheScopesOfTheUnit() {
    assertThatThrownBy(() -> new UndefinedNameCheck("m", Set.of()).matches(ROOT))
        .isInstanceOf(AnalysisFailedException.class);
    assertThatThrownBy(() -> new UnusedBindingCheck("m").matches(ROOT))
        .isInstanceOf(AnalysisFailedException.class);
  }
}
