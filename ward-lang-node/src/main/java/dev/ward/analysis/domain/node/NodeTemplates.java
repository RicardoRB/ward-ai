package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;

/** Reads the template literals of TypeScript and JavaScript expressions. */
final class NodeTemplates {
  static final String SINGLE = "singleExpression";
  private static final String LITERAL = "literal";
  private static final String TEMPLATE = "templateStringLiteral";
  private static final String ATOM = "templateStringAtom";

  private NodeTemplates() {}

  /** Whether the node is a literal part of an expression. */
  static boolean isLiteral(final SyntaxNode part) {
    return LITERAL.equals(part.kind());
  }

  /** The template literal that a string or template expression is, if it is one. */
  static Optional<SyntaxNode> of(final SyntaxNode value) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(value);
    if (!SINGLE.equals(value.kind()) || parts.size() != 1 || !isLiteral(parts.get(0))) {
      return Optional.empty();
    }
    final SyntaxNode literal = NodeFlowSyntax.parts(parts.get(0)).get(0);
    return TEMPLATE.equals(literal.kind()) ? Optional.of(literal) : Optional.empty();
  }

  /** The expression of a {@code ${...}} atom, or none for a text atom. */
  static Optional<SyntaxNode> expression(final SyntaxNode atom) {
    return ATOM.equals(atom.kind())
        ? Optional.ofNullable(NodeFlowSyntax.firstOf(NodeFlowSyntax.parts(atom), SINGLE))
        : Optional.empty();
  }
}
