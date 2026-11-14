package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.SyntaxNode;

/** Lowers {@code return}, {@code throw}, {@code break} and {@code continue} of a Kotlin body. */
final class KotlinJumpLowering {

  private static final String EXPRESSION = "expression";
  private static final String RETURN = "return";
  private static final String AT = "@";

  private final ControlFlowBuilder flow;
  private final KotlinValueLowering values;

  KotlinJumpLowering(final ControlFlowBuilder flow, final KotlinValueLowering values) {
    this.flow = flow;
    this.values = values;
  }

  /** Lowers {@code return}, {@code throw}, {@code break} and {@code continue}. */
  void jump(final SyntaxNode node) {
    final String keyword = KotlinFlowSyntax.tokenText(KotlinFlowSyntax.parts(node).get(0));
    if (RETURN.equals(keyword)) {
      values.expression(node, KotlinFlowSyntax.mayThrow(node), null);
      flow.tries().returnFrom();
    } else if (KotlinFlowSyntax.THROW.equals(keyword)) {
      values.expression(node, true, null);
      flow.tries().throwOut();
    } else if (keyword.startsWith("break")) {
      flow.add(node, false);
      flow.jumps().breakTo(labelOf(keyword));
    } else if (keyword.startsWith("continue")) {
      flow.add(node, false);
      flow.jumps().continueTo(labelOf(keyword));
    } else {
      labelledReturn(node, keyword);
    }
  }

  /** A labelled return leaves the lambda of that label with its value; else it is one node. */
  private void labelledReturn(final SyntaxNode node, final String keyword) {
    final SyntaxNode value = KotlinFlowSyntax.child(node, EXPRESSION);
    if (!values.leaveLambda(labelOf(keyword), value)) {
      values.expression(node, KotlinFlowSyntax.mayThrow(node), null);
    }
  }

  private static String labelOf(final String keyword) {
    final int at = keyword.indexOf(AT);
    return at < 0 ? null : keyword.substring(at + 1);
  }
}
