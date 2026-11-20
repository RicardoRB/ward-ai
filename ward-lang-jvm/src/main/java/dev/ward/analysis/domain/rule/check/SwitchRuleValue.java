package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/**
 * Tells whether an expression statement is the value of a switch rule ({@code case X -> value;})
 * in a switch used as an expression, so its result is the value of the switch and is not dropped.
 */
final class SwitchRuleValue {
  private static final List<String> PATH =
      List.of(
          "blockStatement",
          "switchRuleOutcome",
          "switchLabeledRule",
          "switchExpression",
          "expression"
      );

  private SwitchRuleValue() {}

  /**
   * Returns whether the statement whose ancestors are {@code ancestors} gives the value of a switch
   * expression: its parents are a switch rule's outcome and a switch that is itself an expression,
   * not a switch statement.
   *
   * @param ancestors the nodes above the statement, root first
   */
  static boolean yields(final List<SyntaxNode> ancestors) {
    final int size = ancestors.size();
    if (size < PATH.size()) {
      return false;
    }
    for (int step = 0; step < PATH.size(); step++) {
      if (!PATH.get(step).equals(ancestors.get(size - 1 - step).kind())) {
        return false;
      }
    }
    return true;
  }
}
