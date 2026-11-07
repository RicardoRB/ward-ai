package dev.ward.analysis.domain.swift;

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
import dev.ward.analysis.domain.scope.ScopeBuilder;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SwiftHiddenFieldCheckTest {

  private static final SyntaxNode ROOT = SwiftCheckNodes.node("file", 0, 0);
  private static final SourceUnit EMPTY = new SourceUnit("m.swift", AnalysisLanguage.SWIFT, "");
  private static final String INIT_TEXT = "init(x: Int) {}";
  private static final int VALUE_5 = 5;
  private static final int VALUE_6 = 6;
  private static final int VALUE_10 = 10;
  private static final int VALUE_11 = 11;
  private static final int VALUE_15 = 15;
  private static final int VALUE_51 = 51;
  private static final int VALUE_60 = 60;
  private static final int VALUE_61 = 61;
  private static final int VALUE_65 = 65;
  private static final int VALUE_80 = 80;
  private static final int VALUE_90 = 90;
  private static final int VALUE_100 = 100;

  private static List<RuleMatch> hiddenField(final UnitScopes scopes, final SourceUnit unit) {
    return new SwiftHiddenFieldCheck("{value} hides")
        .matches(ROOT, FlowView.NONE, SwiftCheckNodes.view(scopes), unit);
  }

  private static UnitScopes memberThenParameter(final String memberName, final String paramName) {
    final ScopeBuilder builder =
        new ScopeBuilder(SwiftCheckNodes.span(0, VALUE_100), ClassScopes.VISIBLE);
    builder.open(ScopeKind.CLASS, SwiftCheckNodes.span(0, VALUE_90));
    builder.declare(
        memberName, BindingKind.VARIABLE, SwiftCheckNodes.span(VALUE_10, VALUE_11), Set.of());
    builder.open(ScopeKind.FUNCTION, SwiftCheckNodes.span(VALUE_51, VALUE_80));
    builder.declare(
        paramName, BindingKind.PARAMETER, SwiftCheckNodes.span(VALUE_60, VALUE_61), Set.of());
    builder.close();
    builder.close();
    return builder.build();
  }

  @Test
  void hiddenFieldReportsParameterNamedLikeMemberOfEnclosingType() {
    final List<RuleMatch> found = hiddenField(memberThenParameter("x", "x"), EMPTY);

    assertThat(found).hasSize(1);
    assertThat(found.get(0).message()).isEqualTo("x hides");
    assertThat(found.get(0).span().startOffset()).isEqualTo(VALUE_60);
  }

  @Test
  void hiddenFieldIgnoresInitializerParameters() {
    final SourceUnit init = new SourceUnit("m.swift", AnalysisLanguage.SWIFT, INIT_TEXT);
    final ScopeBuilder builder =
        new ScopeBuilder(SwiftCheckNodes.span(0, VALUE_100), ClassScopes.VISIBLE);
    builder.open(ScopeKind.CLASS, SwiftCheckNodes.span(0, VALUE_90));
    builder.declare("x", BindingKind.VARIABLE, SwiftCheckNodes.span(VALUE_10, VALUE_11), Set.of());
    builder.open(ScopeKind.FUNCTION, SwiftCheckNodes.span(0, VALUE_15));
    builder.declare("x", BindingKind.PARAMETER, SwiftCheckNodes.span(VALUE_5, VALUE_6), Set.of());
    builder.close();
    builder.close();

    assertThat(hiddenField(builder.build(), init)).isEmpty();
  }

  @Test
  void hiddenFieldReportsLocalNamedLikeMember() {
    final ScopeBuilder builder =
        new ScopeBuilder(SwiftCheckNodes.span(0, VALUE_100), ClassScopes.VISIBLE);
    builder.open(ScopeKind.CLASS, SwiftCheckNodes.span(0, VALUE_90));
    builder.declare(
        "count", BindingKind.VARIABLE, SwiftCheckNodes.span(VALUE_10, VALUE_15), Set.of());
    builder.open(ScopeKind.BLOCK, SwiftCheckNodes.span(VALUE_51, VALUE_80));
    builder.declare(
        "count", BindingKind.CONSTANT, SwiftCheckNodes.span(VALUE_60, VALUE_65), Set.of());
    builder.close();
    builder.close();

    assertThat(hiddenField(builder.build(), EMPTY)).hasSize(1);
  }

  @Test
  void hiddenFieldIsSilentWithoutAnEnclosingMemberOfTheSameName() {
    assertThat(hiddenField(memberThenParameter("y", "x"), EMPTY)).isEmpty();
  }

  @Test
  void hiddenFieldNeedsTheScopes() {
    assertThatThrownBy(() -> new SwiftHiddenFieldCheck("m").matches(ROOT))
        .isInstanceOf(AnalysisFailedException.class);
  }
}
