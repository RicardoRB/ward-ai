package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.ScopeAnalysis;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.Scope;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Runs the Kotlin scope checks over hand-made scopes that match the text of a small file. */
class KotlinScopeChecksTest {

  private static final String MESSAGE = "m";
  private static final SyntaxNode ROOT =
      new SyntaxNode("kotlinFile", new Span(1, 1, 1, 1, 0, 0), Map.of(), List.of());
  private static final int BUILTIN = 0;
  private static final int MODULE = 1;
  private static final int CLASS_SCOPE = 2;
  private static final int FUNCTION_SCOPE = 2;

  private static Span span(final String text, final String token) {
    final int start = text.indexOf(token);
    return new Span(1, start + 1, 1, start + token.length() + 1, start, start + token.length());
  }

  private static Span spanAfter(final String text, final String token, final int from) {
    final int start = text.indexOf(token, from);
    return new Span(1, start + 1, 1, start + token.length() + 1, start, start + token.length());
  }

  private static Scope scope(
      final int id,
      final ScopeKind kind,
      final int parent,
      final Span span,
      final List<Binding> bindings) {
    return new Scope(id, kind, parent, span, bindings, false);
  }

  private static Scope builtins(final String text) {
    return scope(BUILTIN, ScopeKind.BUILTIN, Scope.NO_PARENT, span(text, text), List.of());
  }

  private static Scope module(final String text, final List<Binding> bindings) {
    return scope(MODULE, ScopeKind.MODULE, BUILTIN, span(text, text), bindings);
  }

  private static Binding binding(
      final String name,
      final BindingKind kind,
      final Span declaration,
      final int scope,
      final BindingFlag... flags) {
    return new Binding(name, kind, declaration, Set.of(flags), scope, Optional.empty());
  }

  private static NameReference use(
      final Span span, final ReferenceAccess access, final int scope, final Binding resolved) {
    return new NameReference("x", span, access, scope, Optional.ofNullable(resolved), false);
  }

  private static ScopeView view(final UnitScopes scopes) {
    return new ScopeView(
        scopes,
        Map.of(),
        Set.of(),
        new ScopeAnalysis() {
          @Override
          public UnitScopes analyze(final SourceUnit unit, final SyntaxNode tree) {
            return scopes;
          }

          @Override
          public Set<String> builtins(final Set<String> environments) {
            return Set.of();
          }
        });
  }

  private static SourceUnit unit(final String text) {
    return new SourceUnit("src/Main.kt", AnalysisLanguage.KOTLIN, text);
  }

  @Nested
  class UnusedParameter {

    @Test
    void reportsAParameterTheBodyNeverReads() {
      final String text = "fun f(a: Int, b: Int) = a";
      final Binding a = binding("a", BindingKind.PARAMETER, span(text, "a: Int"), FUNCTION_SCOPE);
      final Binding b = binding("b", BindingKind.PARAMETER, span(text, "b: Int"), FUNCTION_SCOPE);
      final UnitScopes scopes =
          new UnitScopes(
              List.of(
                  builtins(text),
                  module(text, List.of()),
                  scope(
                      FUNCTION_SCOPE, ScopeKind.FUNCTION, MODULE, span(text, text), List.of(a, b))),
              List.of(use(spanAfter(text, "a", 20), ReferenceAccess.READ, FUNCTION_SCOPE, a)));

      final List<RuleMatch> found =
          new KotlinUnusedParameterCheck(MESSAGE)
              .matches(ROOT, FlowView.NONE, view(scopes), unit(text));

      assertThat(found).hasSize(1);
      assertThat(found.get(0).span().startOffset()).isEqualTo(b.declaration().startOffset());
    }

    @Test
    void skipsAnOverrideFunction() {
      final String text = "override fun f(a: Int) = 1";
      final Binding a = binding("a", BindingKind.PARAMETER, span(text, "a: Int"), FUNCTION_SCOPE);
      final UnitScopes scopes =
          new UnitScopes(
              List.of(
                  builtins(text),
                  module(text, List.of()),
                  scope(FUNCTION_SCOPE, ScopeKind.FUNCTION, MODULE, span(text, text), List.of(a))),
              List.of());

      assertThat(
              new KotlinUnusedParameterCheck(MESSAGE)
                  .matches(ROOT, FlowView.NONE, view(scopes), unit(text)))
          .isEmpty();
    }
  }

  @Nested
  class UnusedPrivateMember {

    @Test
    void reportsAPrivateFunctionThatTheFileNeverMentions() {
      final String text = "class C {\n    private fun reset() = 0\n    fun total() = 1\n}";
      final Binding reset =
          binding("reset", BindingKind.FUNCTION, span(text, "reset"), CLASS_SCOPE);
      final UnitScopes scopes =
          new UnitScopes(
              List.of(
                  builtins(text),
                  module(text, List.of()),
                  scope(CLASS_SCOPE, ScopeKind.CLASS, MODULE, span(text, text), List.of(reset))),
              List.of());

      assertThat(
              new KotlinUnusedPrivateMemberCheck(MESSAGE)
                  .matches(ROOT, FlowView.NONE, view(scopes), unit(text)))
          .hasSize(1);
    }

    @Test
    void acceptsAPrivateFunctionAnotherMemberCalls() {
      final String text = "class C {\n    private fun reset() = 0\n    fun total() = reset()\n}";
      final Binding reset =
          binding("reset", BindingKind.FUNCTION, span(text, "reset"), CLASS_SCOPE);
      final UnitScopes scopes =
          new UnitScopes(
              List.of(
                  builtins(text),
                  module(text, List.of()),
                  scope(CLASS_SCOPE, ScopeKind.CLASS, MODULE, span(text, text), List.of(reset))),
              List.of());

      assertThat(
              new KotlinUnusedPrivateMemberCheck(MESSAGE)
                  .matches(ROOT, FlowView.NONE, view(scopes), unit(text)))
          .isEmpty();
    }
  }

  @Nested
  class UnusedPrivateClass {

    @Test
    void reportsAPrivateClassThatTheFileNeverMentions() {
      final String text = "private class Helper\n\nfun use() = 1";
      final Binding helper = binding("Helper", BindingKind.CLASS, span(text, "Helper"), MODULE);
      final UnitScopes scopes =
          new UnitScopes(List.of(builtins(text), module(text, List.of(helper))), List.of());

      assertThat(
              new KotlinUnusedPrivateClassCheck(MESSAGE)
                  .matches(ROOT, FlowView.NONE, view(scopes), unit(text)))
          .hasSize(1);
    }
  }

  @Nested
  class VarCouldBeVal {

    @Test
    void reportsALocalVarThatIsNeverAssignedAgain() {
      final String text = "fun t() {\n    var sum = 0\n    return sum\n}";
      final Span declaration = span(text, "sum");
      final Binding sum = binding("sum", BindingKind.VARIABLE, declaration, FUNCTION_SCOPE);
      final UnitScopes scopes =
          new UnitScopes(
              List.of(
                  builtins(text),
                  module(text, List.of()),
                  scope(
                      FUNCTION_SCOPE, ScopeKind.FUNCTION, MODULE, span(text, text), List.of(sum))),
              List.of(use(declaration, ReferenceAccess.WRITE, FUNCTION_SCOPE, sum)));

      assertThat(
              new KotlinVarCouldBeValCheck(MESSAGE)
                  .matches(ROOT, FlowView.NONE, view(scopes), unit(text)))
          .hasSize(1);
    }

    @Test
    void acceptsALocalVarThatIsAssignedAgain() {
      final String text = "fun t() {\n    var sum = 0\n    sum = 1\n    return sum\n}";
      final Span declaration = span(text, "sum");
      final Binding sum = binding("sum", BindingKind.VARIABLE, declaration, FUNCTION_SCOPE);
      final UnitScopes scopes =
          new UnitScopes(
              List.of(
                  builtins(text),
                  module(text, List.of()),
                  scope(
                      FUNCTION_SCOPE, ScopeKind.FUNCTION, MODULE, span(text, text), List.of(sum))),
              List.of(
                  use(declaration, ReferenceAccess.WRITE, FUNCTION_SCOPE, sum),
                  use(
                      spanAfter(text, "sum", declaration.endOffset()),
                      ReferenceAccess.WRITE,
                      FUNCTION_SCOPE,
                      sum)));

      assertThat(
              new KotlinVarCouldBeValCheck(MESSAGE)
                  .matches(ROOT, FlowView.NONE, view(scopes), unit(text)))
          .isEmpty();
    }
  }

  @Nested
  class NestedClassesVisibility {

    @Test
    void reportsAnExplicitPublicNestedClassOfAnInternalClass() {
      final String text = "internal class Outer {\n    public class Inner\n}";
      final Binding outer = binding("Outer", BindingKind.CLASS, span(text, "Outer"), MODULE);
      final Binding inner = binding("Inner", BindingKind.CLASS, span(text, "Inner"), CLASS_SCOPE);
      final UnitScopes scopes =
          new UnitScopes(
              List.of(
                  builtins(text),
                  module(text, List.of(outer)),
                  scope(CLASS_SCOPE, ScopeKind.CLASS, MODULE, span(text, text), List.of(inner))),
              List.of());

      final List<RuleMatch> found =
          new KotlinNestedClassesVisibilityCheck(MESSAGE)
              .matches(ROOT, FlowView.NONE, view(scopes), unit(text));

      assertThat(found).hasSize(1);
      assertThat(found.get(0).span().startOffset()).isEqualTo(inner.declaration().startOffset());
    }

    @Test
    void acceptsANestedClassOfAPublicClass() {
      final String text = "class Outer {\n    public class Inner\n}";
      final Binding outer = binding("Outer", BindingKind.CLASS, span(text, "Outer"), MODULE);
      final Binding inner = binding("Inner", BindingKind.CLASS, span(text, "Inner"), CLASS_SCOPE);
      final UnitScopes scopes =
          new UnitScopes(
              List.of(
                  builtins(text),
                  module(text, List.of(outer)),
                  scope(CLASS_SCOPE, ScopeKind.CLASS, MODULE, span(text, text), List.of(inner))),
              List.of());

      assertThat(
              new KotlinNestedClassesVisibilityCheck(MESSAGE)
                  .matches(ROOT, FlowView.NONE, view(scopes), unit(text)))
          .isEmpty();
    }
  }
}
