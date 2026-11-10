package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
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

class SwiftVariableDistanceCheckTest {

  private static final SyntaxNode ROOT = SwiftTestNodes.node("file", 0, 0);
  private static final SourceUnit EMPTY = new SourceUnit("m.swift", AnalysisLanguage.SWIFT, "");
  private static final int VALUE_3 = 3;
  private static final int VALUE_5 = 5;
  private static final int VALUE_10 = 10;
  private static final int VALUE_20 = 20;
  private static final int VALUE_30 = 30;
  private static final int VALUE_40 = 40;
  private static final int VALUE_42 = 42;
  private static final int VALUE_43 = 43;
  private static final int VALUE_50 = 50;
  private static final int VALUE_60 = 60;

  private static UnitScopes useAfterDeclaration(final int use) {
    final ScopeBuilder builder =
        new ScopeBuilder(SwiftTestNodes.span(0, VALUE_60), ClassScopes.VISIBLE);
    builder.open(ScopeKind.BLOCK, SwiftTestNodes.span(0, VALUE_60));
    builder.declare("total", BindingKind.VARIABLE, SwiftTestNodes.span(2, VALUE_5), Set.of());
    builder.reference("total", SwiftTestNodes.span(use, use + 1), ReferenceAccess.READ);
    builder.close();
    return builder.build();
  }

  private static SyntaxNode fiveStatements() {
    return SwiftTestNodes.node(
        "file",
        0,
        VALUE_60,
        SwiftTestNodes.node("statement", 0, VALUE_10),
        SwiftTestNodes.node("statement", VALUE_10, VALUE_20),
        SwiftTestNodes.node("statement", VALUE_20, VALUE_30),
        SwiftTestNodes.node("statement", VALUE_30, VALUE_40),
        SwiftTestNodes.node("statement", VALUE_40, VALUE_50));
  }

  private static List<RuleMatch> distance(final int max, final UnitScopes scopes) {
    return new VariableDistanceCheck(max, "{value} read after {max}")
        .matches(fiveStatements(), FlowView.NONE, SwiftTestNodes.view(scopes), EMPTY);
  }

  @Test
  void variableDistanceReportsFirstReadBeyondTheLimit() {
    final List<RuleMatch> found = distance(2, useAfterDeclaration(VALUE_42));

    assertThat(found).hasSize(1);
    assertThat(found.get(0).message()).isEqualTo("total read after 2");
    assertThat(found.get(0).span().startOffset()).isEqualTo(2);
  }

  @Test
  void variableDistanceAcceptsAFirstReadWithinTheLimit() {
    assertThat(distance(VALUE_3, useAfterDeclaration(VALUE_42))).isEmpty();
  }

  @Test
  void variableDistanceIgnoresReadsThatDoNotFollowTheDeclaration() {
    assertThat(distance(0, useAfterDeclaration(0))).isEmpty();
  }

  @Test
  void variableDistanceIgnoresConstants() {
    final ScopeBuilder builder =
        new ScopeBuilder(SwiftTestNodes.span(0, VALUE_60), ClassScopes.VISIBLE);
    builder.open(ScopeKind.BLOCK, SwiftTestNodes.span(0, VALUE_60));
    builder.declare("total", BindingKind.CONSTANT, SwiftTestNodes.span(2, VALUE_5), Set.of());
    builder.reference("total", SwiftTestNodes.span(VALUE_42, VALUE_43), ReferenceAccess.READ);
    builder.close();

    assertThat(distance(0, builder.build())).isEmpty();
  }

  @Test
  void variableDistanceNeedsTheScopes() {
    assertThatThrownBy(() -> new VariableDistanceCheck(1, "m").matches(ROOT))
        .isInstanceOf(AnalysisFailedException.class);
  }
}
