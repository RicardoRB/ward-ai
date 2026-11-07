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
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ClassScopes;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.ScopeBuilder;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The reassigned-binding check on hand-built scopes, for each binding kind it reports. */
class ReassignedBindingCheckTest {

  private static final String MESSAGE = "'{value}' is read-only";
  private static final int READ_OFFSET = 3;
  private static final SyntaxNode ROOT =
      new SyntaxNode("file", new Span(1, 1, 1, 1, 0, 0), Map.of(), List.of());

  private static Span at(final int offset) {
    return new Span(1, offset + 1, 1, offset + 2, offset, offset + 1);
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

  private static List<RuleMatch> run(final ReassignedBindingCheck check, final UnitScopes scopes) {
    return check.matches(
        ROOT, FlowView.NONE, view(scopes), new SourceUnit("m", AnalysisLanguage.JAVASCRIPT, ""));
  }

  @Test
  void writesToAConstantAreReportedWithItsName() {
    final ScopeBuilder builder = new ScopeBuilder(at(0), ClassScopes.VISIBLE);
    builder.declare("limit", BindingKind.CONSTANT, at(1), Set.of());
    builder.reference("limit", at(2), ReferenceAccess.WRITE);
    builder.reference("limit", at(READ_OFFSET), ReferenceAccess.READ);

    final List<RuleMatch> found =
        run(
            new ReassignedBindingCheck(Set.of(BindingKind.CONSTANT), "'{value}' is constant"),
            builder.build());

    assertThat(found).extracting(RuleMatch::message).containsExactly("'limit' is constant");
    assertThat(found).extracting(match -> match.span().startOffset()).containsExactly(2);
  }

  @Test
  void theDeclaringReferenceIsNotAWriteToReport() {
    final ScopeBuilder builder = new ScopeBuilder(at(0), ClassScopes.VISIBLE);
    builder.declare("limit", BindingKind.CONSTANT, at(1), Set.of());
    builder.reference("limit", at(1), ReferenceAccess.WRITE);

    assertThat(
            run(new ReassignedBindingCheck(Set.of(BindingKind.CONSTANT), MESSAGE), builder.build()))
        .isEmpty();
  }

  @Test
  void bindingsOfOtherKindsAreNotReported() {
    final ScopeBuilder builder = new ScopeBuilder(at(0), ClassScopes.VISIBLE);
    builder.declare("count", BindingKind.VARIABLE, at(1), Set.of());
    builder.reference("count", at(2), ReferenceAccess.WRITE);

    assertThat(
            run(new ReassignedBindingCheck(Set.of(BindingKind.CONSTANT), MESSAGE), builder.build()))
        .isEmpty();
  }

  @Test
  void theKindsAreNamedWhenTheCheckIsBuiltFromConfiguration() {
    final ScopeBuilder builder = new ScopeBuilder(at(0), ClassScopes.VISIBLE);
    builder.declare("parse", BindingKind.FUNCTION, at(1), Set.of());
    builder.reference("parse", at(2), ReferenceAccess.WRITE);

    final List<RuleMatch> found =
        run(
            ReassignedBindingCheck.of(List.of("FUNCTION"), "'{value}' is a function"),
            builder.build());

    assertThat(found).extracting(RuleMatch::message).containsExactly("'parse' is a function");
  }

  @Test
  void anUnknownKindNameIsRejected() {
    assertThatThrownBy(() -> ReassignedBindingCheck.of(List.of("NOT_A_KIND"), MESSAGE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void theCheckCannotRunWithoutScopes() {
    assertThatThrownBy(
            () -> new ReassignedBindingCheck(Set.of(BindingKind.CONSTANT), MESSAGE).matches(ROOT))
        .isInstanceOf(AnalysisFailedException.class);
  }
}
