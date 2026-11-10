package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.python.PythonFlowSyntax;
import dev.ward.analysis.domain.python.PythonStatementLeaves;
import java.util.List;
import java.util.Set;

/**
 * Reports whether a Python statement ends a body with a return, a raise or a call that never
 * returns, the way Pylint's return-ended test decides it.
 */
final class ReturnEnds {

  private static final String RETURN = "return_stmt";
  private static final String RAISE = "raise_stmt";
  private static final String ASSERT = "assert_stmt";
  private static final String IF = "if_stmt";
  private static final String WHILE = "while_stmt";
  private static final String TRY = "try_stmt";
  private static final String BLOCK = "block";
  private static final String ELSE = "else";
  private static final String EXCEPT = "except_clause";
  private static final String FUNCTION = "funcdef";
  private static final String CLASS = "classdef";
  private static final String LAMBDA = "lambdef";
  private static final String LAMBDA_NO_CONDITION = "lambdef_nocond";
  private static final Set<String> FALSE_LITERALS = Set.of("False", "0");
  private static final Set<String> EXIT_CALLS = Set.of("sys.exit", "os._exit", "exit", "quit");
  private static final int CALL_HEAD_TOKENS = 4;

  private ReturnEnds() {}

  /** Reports whether the statements of a block end it with a return. */
  static boolean endsInReturn(final SyntaxNode block) {
    return anyEnds(PythonStatementLeaves.leaves(block), false);
  }

  /** Reports whether any statement in the list ends the body, given whether a return follows. */
  private static boolean anyEnds(final List<SyntaxNode> statements, final boolean returnAfter) {
    for (int index = 0; index < statements.size(); index++) {
      final boolean later = returnAfter || hasReturnAfter(statements, index);
      if (ends(statements.get(index), later)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasReturnAfter(final List<SyntaxNode> statements, final int index) {
    for (int next = index + 1; next < statements.size(); next++) {
      if (RETURN.equals(statements.get(next).kind())) {
        return true;
      }
    }
    return false;
  }

  /** Reports whether one statement ends the body, the way Pylint's return-ended test does. */
  private static boolean ends(final SyntaxNode node, final boolean returnAfter) {
    if (isScope(node)) {
      return false;
    }
    return switch (node.kind()) {
      case RETURN, RAISE -> true;
      case ASSERT -> assertsFalse(node);
      case IF -> ifEnds(node, returnAfter);
      case WHILE -> ReturnLoops.whileEnds(node);
      case TRY -> tryEnds(node);
      default -> callsExit(node) || anyChildEnds(node);
    };
  }

  private static boolean isScope(final SyntaxNode node) {
    return FUNCTION.equals(node.kind())
        || CLASS.equals(node.kind())
        || LAMBDA.equals(node.kind())
        || LAMBDA_NO_CONDITION.equals(node.kind());
  }

  private static boolean anyChildEnds(final SyntaxNode node) {
    for (final SyntaxNode child : node.children()) {
      if (ends(child, false)) {
        return true;
      }
    }
    return false;
  }

  private static boolean assertsFalse(final SyntaxNode node) {
    final List<SyntaxNode> tokens = SyntaxWalk.leaves(node);
    return tokens.size() > 1 && FALSE_LITERALS.contains(SyntaxWalk.text(tokens.get(1)));
  }

  private static boolean ifEnds(final SyntaxNode node, final boolean returnAfter) {
    final List<SyntaxNode> blocks = PythonFlowSyntax.children(node, BLOCK);
    if (!PythonFlowSyntax.hasToken(node, ELSE)) {
      return returnAfter && blocks.stream().anyMatch(ReturnEnds::endsInReturn);
    }
    return blocks.stream().allMatch(ReturnEnds::endsInReturn);
  }

  private static boolean tryEnds(final SyntaxNode node) {
    final List<SyntaxNode> handlers = PythonFlowSyntax.children(node, EXCEPT);
    final List<SyntaxNode> blocks = PythonFlowSyntax.children(node, BLOCK);
    final boolean bodyEnds = blocks.stream().limit(1).anyMatch(ReturnEnds::endsInReturn);
    final boolean handlersEnd =
        !handlers.isEmpty()
            && blocks.subList(1, 1 + handlers.size()).stream().allMatch(ReturnEnds::endsInReturn);
    return bodyEnds && handlersEnd;
  }

  /** A call to a function that never returns, such as {@code sys.exit(...)}. */
  private static boolean callsExit(final SyntaxNode node) {
    final List<SyntaxNode> tokens = SyntaxWalk.leaves(node);
    final StringBuilder head = new StringBuilder();
    for (int index = 0; index < Math.min(CALL_HEAD_TOKENS, tokens.size()); index++) {
      head.append(SyntaxWalk.text(tokens.get(index)));
    }
    final String prefix = head.toString();
    return EXIT_CALLS.stream().anyMatch(call -> prefix.startsWith(call + "("));
  }
}
