package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Set;

/** Reads the names, keywords and operators of TypeScript and JavaScript syntax nodes. */
final class NodeSyntax {

  /** Nodes whose contents are types, which name no variable. */
  static final Set<String> SKIPPED = Set.of("typeAnnotation", "typeArguments", "typeParameters");

  private static final String SINGLE = "singleExpression";
  private static final String IDENTIFIER_NAME = "identifierName";
  private static final String IDENTIFIER_OR_KEYWORD = "identifierOrKeyWord";
  private static final String IDENTIFIER = "identifier";
  private static final String LEAF = "Identifier";
  private static final String EQUALS = "=";
  private static final String ASSIGNMENT_OPERATOR = "assignmentOperator";
  private static final String VAR_MODIFIER = "varModifier";
  private static final Set<String> KEYWORDS = Set.of("let", "const", "var");

  private NodeSyntax() {}

  /**
   * Reports whether a node names a variable in an expression. The TypeScript grammar wraps the name
   * in an identifier name; the JavaScript grammar puts the identifier directly in the expression.
   */
  static boolean isName(final String kind) {
    return IDENTIFIER_NAME.equals(kind)
        || IDENTIFIER_OR_KEYWORD.equals(kind)
        || IDENTIFIER.equals(kind);
  }

  /** Returns the first identifier token under a node, which holds the name, or null. */
  static SyntaxNode leafOf(final SyntaxNode node) {
    if (LEAF.equals(node.kind())) {
      return node;
    }
    for (final SyntaxNode child : node.children()) {
      final SyntaxNode leaf = leafOf(child);
      if (leaf != null) {
        return leaf;
      }
    }
    return null;
  }

  /** Returns the identifier of a plain variable reference, or null. */
  static SyntaxNode simpleTarget(final SyntaxNode node) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(node);
    if (SINGLE.equals(node.kind()) && parts.size() == 1 && isName(parts.get(0).kind())) {
      return leafOf(parts.get(0));
    }
    return null;
  }

  /** Reports whether a node is a declaration keyword, such as {@code let}. */
  static boolean isKeyword(final SyntaxNode node) {
    return IDENTIFIER_NAME.equals(node.kind()) && KEYWORDS.contains(NodeFlowSyntax.text(node));
  }

  /** Reports whether a part is an assignment operator, simple or compound. */
  static boolean isAssignment(final SyntaxNode part) {
    return EQUALS.equals(NodeFlowSyntax.text(part)) || ASSIGNMENT_OPERATOR.equals(part.kind());
  }

  /** Reports whether one of the parts has the given token as its text. */
  static boolean hasToken(final List<SyntaxNode> parts, final String token) {
    for (final SyntaxNode part : parts) {
      if (token.equals(NodeFlowSyntax.text(part))) {
        return true;
      }
    }
    return false;
  }

  /** Reports whether a node carries a {@code var}, {@code let} or {@code const} modifier. */
  static boolean modifies(final SyntaxNode node) {
    return NodeFlowSyntax.firstOf(NodeFlowSyntax.parts(node), VAR_MODIFIER) != null;
  }
}
