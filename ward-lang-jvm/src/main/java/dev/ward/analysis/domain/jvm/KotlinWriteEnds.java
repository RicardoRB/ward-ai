package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;

/**
 * Finds where each write of a Kotlin file takes effect: the end of the declaration or assignment
 * that makes it, because the value on the right is evaluated before the name on the left is
 * written. The scope analysis lists the name first.
 */
final class KotlinWriteEnds {

  private static final String ASSIGNMENT_OPERATOR = "assignmentOperator";
  private static final String VARIABLE = "variableDeclaration";
  private static final String MULTI = "multiVariableDeclaration";
  private static final int ASSIGNMENT_PARTS = 3;
  private static final int UPDATE_PARTS = 2;

  private KotlinWriteEnds() {}

  /**
   * Adds the write ends of every declaration and assignment under {@code node}.
   *
   * @param node a node of the syntax tree
   * @param ends the end offset of the construct, by the start offset of the written name
   */
  static void collect(final SyntaxNode node, final Map<Integer, Integer> ends) {
    switch (node.kind()) {
      case "propertyDeclaration" -> declared(node, node.span().endOffset(), ends);
      case "forExpression" -> forLoop(node, ends);
      case "expression" -> assignment(node, ends);
      case "postfixUnaryExpression" -> update(node, 0, ends);
      case "prefixUnaryExpression" -> update(node, 1, ends);
      default -> {
        // Other nodes write nothing themselves.
      }
    }
    node.children().forEach(child -> collect(child, ends));
  }

  private static void forLoop(final SyntaxNode node, final Map<Integer, Integer> ends) {
    declared(node, KotlinFlowSyntax.child(node, "expression").span().endOffset(), ends);
  }

  private static void assignment(final SyntaxNode node, final Map<Integer, Integer> ends) {
    final List<SyntaxNode> parts = KotlinFlowSyntax.parts(node);
    if (parts.size() == ASSIGNMENT_PARTS && ASSIGNMENT_OPERATOR.equals(parts.get(1).kind())) {
      target(parts.get(0), node, ends);
    }
  }

  /** {@code a++} has the name first and {@code ++a} second. */
  private static void update(
      final SyntaxNode node, final int nameIndex, final Map<Integer, Integer> ends) {
    final List<SyntaxNode> parts = KotlinFlowSyntax.parts(node);
    if (parts.size() == UPDATE_PARTS) {
      target(parts.get(nameIndex), node, ends);
    }
  }

  private static void target(
      final SyntaxNode expression, final SyntaxNode statement, final Map<Integer, Integer> ends) {
    final SyntaxNode name = KotlinScopeExpressions.plainName(expression);
    if (name != null) {
      ends.put(name.span().startOffset(), statement.span().endOffset());
    }
  }

  private static void declared(
      final SyntaxNode node, final int end, final Map<Integer, Integer> ends) {
    for (final SyntaxNode part : KotlinFlowSyntax.parts(node)) {
      if (VARIABLE.equals(part.kind())) {
        final SyntaxNode name = KotlinFlowSyntax.child(part, "simpleIdentifier");
        ends.put(name.span().startOffset(), end);
      } else if (MULTI.equals(part.kind())) {
        declared(part, end, ends);
      }
    }
  }
}
