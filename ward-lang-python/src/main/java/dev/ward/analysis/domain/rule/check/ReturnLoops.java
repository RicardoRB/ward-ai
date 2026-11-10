package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.python.PythonFlowSyntax;
import java.util.List;
import java.util.Set;

/** Reports whether a Python {@code while} loop ends a body, as Pylint's return-ended test does. */
final class ReturnLoops {

  private static final String BREAK = "break_stmt";
  private static final String BLOCK = "block";
  private static final String NAMED_EXPR = "namedexpr_test";
  private static final Set<String> LOOPS = Set.of("for_stmt", "while_stmt");
  private static final Set<String> SCOPES =
      Set.of("funcdef", "classdef", "lambdef", "lambdef_nocond");

  private ReturnLoops() {}

  /** A loop with a condition that is always true and no break of its own, or whose else returns. */
  static boolean whileEnds(final SyntaxNode node) {
    final SyntaxNode condition = PythonFlowSyntax.child(node, NAMED_EXPR);
    final List<SyntaxNode> blocks = PythonFlowSyntax.children(node, BLOCK);
    final boolean alwaysTrue = condition != null && PythonFlowSyntax.isAlwaysTrue(condition);
    final boolean exitsEarly = !blocks.isEmpty() && hasBreak(blocks.get(0));
    final boolean elseEnds = blocks.size() > 1 && ReturnEnds.endsInReturn(blocks.get(1));
    return (alwaysTrue && !exitsEarly) || elseEnds;
  }

  /** A {@code break} that leaves this loop, not one of a nested loop or scope. */
  private static boolean hasBreak(final SyntaxNode node) {
    if (SCOPES.contains(node.kind()) || LOOPS.contains(node.kind())) {
      return false;
    }
    return BREAK.equals(node.kind()) || node.children().stream().anyMatch(ReturnLoops::hasBreak);
  }
}
