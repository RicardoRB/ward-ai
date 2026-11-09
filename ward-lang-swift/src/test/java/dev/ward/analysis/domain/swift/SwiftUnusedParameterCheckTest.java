package dev.ward.analysis.domain.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ClassScopes;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.ScopeBuilder;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SwiftUnusedParameterCheckTest {

  private static final SyntaxNode ROOT = SwiftCheckNodes.node("file", 0, 0);
  private static final String PLAIN = "func move(x: Int) {}";
  private static final String OVERRIDE = "override func move(x: Int) {}";
  private static final String PUBLIC = "public func move(x: Int) {}";
  private static final int FUNCTION_END = 20;
  private static final int UNIT_END = 40;
  private static final int PARAMETER_START = 10;
  private static final int PARAMETER_END = 11;
  private static final int USE_START = 30;
  private static final int USE_END = 31;
  private static final int CLASS_END = 25;
  private static final String MESSAGE = "parameter '{value}' is never used";

  private static List<RuleMatch> unusedParameters(final String text, final UnitScopes scopes) {
    final SourceUnit unit = new SourceUnit("m.swift", AnalysisLanguage.SWIFT, text);
    return new SwiftUnusedParameterCheck(MESSAGE)
        .matches(ROOT, FlowView.NONE, SwiftCheckNodes.view(scopes), unit);
  }

  /** A function whose parameter {@code name} is declared, with the body optionally reading it. */
  private static UnitScopes function(final String name, final boolean read) {
    final ScopeBuilder builder =
        new ScopeBuilder(SwiftCheckNodes.span(0, UNIT_END), ClassScopes.VISIBLE);
    builder.open(ScopeKind.FUNCTION, SwiftCheckNodes.span(0, FUNCTION_END));
    builder.declare(
        name,
        BindingKind.PARAMETER,
        SwiftCheckNodes.span(PARAMETER_START, PARAMETER_END),
        Set.of());
    if (read) {
      builder.reference(name, SwiftCheckNodes.span(USE_START, USE_END), ReferenceAccess.READ);
    }
    builder.close();
    return builder.build();
  }

  /** A class scope, open or closed, holding a function that declares parameter {@code x}. */
  private static UnitScopes methodOf(final boolean open) {
    final ScopeBuilder builder =
        new ScopeBuilder(SwiftCheckNodes.span(0, UNIT_END), ClassScopes.VISIBLE);
    final int type = builder.open(ScopeKind.CLASS, SwiftCheckNodes.span(0, CLASS_END));
    if (open) {
      builder.markOpen(type);
    }
    builder.open(ScopeKind.FUNCTION, SwiftCheckNodes.span(0, FUNCTION_END));
    builder.declare(
        "x", BindingKind.PARAMETER, SwiftCheckNodes.span(PARAMETER_START, PARAMETER_END), Set.of());
    builder.close();
    builder.close();
    return builder.build();
  }

  @Test
  void reportsParameterThatTheBodyNeverReads() {
    final List<RuleMatch> found = unusedParameters(PLAIN, function("x", false));

    assertThat(found).hasSize(1);
    assertThat(found.get(0).message()).isEqualTo("parameter 'x' is never used");
    assertThat(found.get(0).span().startOffset()).isEqualTo(PARAMETER_START);
  }

  @Test
  void keepsParameterThatTheBodyReads() {
    assertThat(unusedParameters(PLAIN, function("x", true))).isEmpty();
  }

  @Test
  void ignoresParameterWhoseNameStartsWithUnderscore() {
    assertThat(unusedParameters(PLAIN, function("_x", false))).isEmpty();
  }

  @Test
  void leavesOverridesAlone() {
    assertThat(unusedParameters(OVERRIDE, function("x", false))).isEmpty();
  }

  @Test
  void leavesPublicFunctionsAlone() {
    assertThat(unusedParameters(PUBLIC, function("x", false))).isEmpty();
  }

  @Test
  void leavesMethodsOfOpenTypesAlone() {
    assertThat(unusedParameters(PLAIN, methodOf(true))).isEmpty();
  }

  @Test
  void reportsMethodOfClosedType() {
    assertThat(unusedParameters(PLAIN, methodOf(false))).hasSize(1);
  }
}
