package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import org.junit.jupiter.api.Test;

class RedundantInstanceofCheckTest {

  private static final int OPERATOR = 2;
  private static final int TARGET = 3;
  private static final String STRING = "java.lang.String";
  private static final RedundantInstanceofCheck CHECK = new RedundantInstanceofCheck("message");

  private static SyntaxNode test(final SyntaxNode target) {
    return MoreTrees.node(
        "expression",
        1,
        MoreTrees.node("expression", 1),
        MoreTrees.leaf("'instanceof'", "instanceof", OPERATOR),
        target);
  }

  private static SyntaxNode typeTarget() {
    return MoreTrees.node("typeType", TARGET);
  }

  private static UnitTypeFacts facts(final int operandDepth, final boolean primitive) {
    return MoreTrees.table(
        1,
        MoreTrees.typed(MoreTrees.type(STRING, operandDepth, primitive)),
        TARGET,
        MoreTrees.typed(MoreTrees.type(STRING, 0, false)));
  }

  private static List<RuleMatch> run(final SyntaxNode root, final UnitTypeFacts types) {
    return CHECK.matches(root, FlowView.NONE, ScopeView.NONE, types, Calls.UNIT);
  }

  @Test
  void aTestOfTheStaticTypeItselfIsReported() {
    assertThat(CHECK.needs().facts()).containsExactly(FactKind.TYPE);
    assertThat(CHECK.matches(test(typeTarget()))).isEmpty();
    assertThat(run(test(typeTarget()), facts(0, false))).hasSize(1);
  }

  @Test
  void aPatternTargetIsReadThroughItsType() {
    final SyntaxNode pattern = MoreTrees.node("pattern", TARGET, typeTarget());

    assertThat(run(test(pattern), facts(0, false))).hasSize(1);
  }

  @Test
  void otherTypesDepthsPrimitivesAndUnknownFactsAreNotReported() {
    assertThat(run(test(typeTarget()), facts(1, false))).isEmpty();
    assertThat(run(test(typeTarget()), facts(0, true))).isEmpty();
    assertThat(run(test(typeTarget()), UnitTypeFacts.none())).isEmpty();
    assertThat(
            run(
                test(typeTarget()),
                MoreTrees.table(
                    1,
                    MoreTrees.typed(MoreTrees.type("java.lang.Object", 0, false)),
                    TARGET,
                    MoreTrees.typed(MoreTrees.type(STRING, 0, false)))))
        .isEmpty();
  }

  @Test
  void anExpressionWithoutAnInstanceofIsSkipped() {
    final SyntaxNode other =
        MoreTrees.node(
            "expression",
            1,
            MoreTrees.node("expression", 1),
            MoreTrees.leaf("'+'", "+", OPERATOR),
            typeTarget());

    assertThat(run(other, facts(0, false))).isEmpty();
    assertThat(run(MoreTrees.node("expression", 1), facts(0, false))).isEmpty();
  }
}
