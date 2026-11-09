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

/** The unused-parameter check on hand-built scopes: parameters no function body reads. */
class UnusedParameterCheckTest {

  private static final String MESSAGE = "parameter '{value}' is never used";
  private static final SyntaxNode ROOT =
      new SyntaxNode("file", new Span(1, 1, 1, 1, 0, 0), Map.of(), List.of());
  private static final SourceUnit UNIT = new SourceUnit("m", AnalysisLanguage.JAVA, "");
  private static final int CLASS_OFFSET = 0;
  private static final int FUNCTION_OFFSET = 10;
  private static final int PARAMETER_OFFSET = 15;
  private static final int USE_OFFSET = 30;

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

  private static List<RuleMatch> run(final UnusedParameterCheck check, final UnitScopes scopes) {
    return check.matches(ROOT, FlowView.NONE, view(scopes), UNIT);
  }

  /** A function declaring parameter {@code name}, read by the body when {@code read} is true. */
  private static UnitScopes function(final String name, final boolean read) {
    final ScopeBuilder builder = new ScopeBuilder(at(0), ClassScopes.VISIBLE);
    builder.open(ScopeKind.FUNCTION, at(FUNCTION_OFFSET));
    builder.declare(name, BindingKind.PARAMETER, at(PARAMETER_OFFSET), Set.of());
    if (read) {
      builder.reference(name, at(USE_OFFSET), ReferenceAccess.READ);
    }
    builder.close();
    return builder.build();
  }

  /** A class holding a function with parameter {@code x}, the class open or closed. */
  private static UnitScopes method(final boolean open) {
    final ScopeBuilder builder = new ScopeBuilder(at(0), ClassScopes.VISIBLE);
    final int type = builder.open(ScopeKind.CLASS, at(CLASS_OFFSET));
    if (open) {
      builder.markOpen(type);
    }
    builder.open(ScopeKind.FUNCTION, at(FUNCTION_OFFSET));
    builder.declare("x", BindingKind.PARAMETER, at(PARAMETER_OFFSET), Set.of());
    builder.close();
    builder.close();
    return builder.build();
  }

  @Test
  void reportsParameterThatNoBodyReadsWithItsName() {
    final List<RuleMatch> found = run(new UnusedParameterCheck(MESSAGE), function("x", false));

    assertThat(found).extracting(RuleMatch::message).containsExactly("parameter 'x' is never used");
    assertThat(found)
        .extracting(match -> match.span().startOffset())
        .containsExactly(PARAMETER_OFFSET);
  }

  @Test
  void keepsParameterThatTheBodyReads() {
    assertThat(run(new UnusedParameterCheck(MESSAGE), function("x", true))).isEmpty();
  }

  @Test
  void ignoresParameterWhoseNameStartsWithUnderscore() {
    assertThat(run(new UnusedParameterCheck(MESSAGE), function("_x", false))).isEmpty();
  }

  @Test
  void leavesParametersOfMethodsOfOpenTypesAlone() {
    assertThat(run(new UnusedParameterCheck(MESSAGE), method(true))).isEmpty();
  }

  @Test
  void reportsParametersOfMethodsOfClosedTypes() {
    assertThat(run(new UnusedParameterCheck(MESSAGE), method(false))).hasSize(1);
  }

  @Test
  void theFilterDecidesWhichFunctionsAreChecked() {
    final UnusedParameterCheck.FunctionFilter none = (function, unit) -> false;

    assertThat(run(new UnusedParameterCheck(MESSAGE, none), function("x", false))).isEmpty();
  }
}
