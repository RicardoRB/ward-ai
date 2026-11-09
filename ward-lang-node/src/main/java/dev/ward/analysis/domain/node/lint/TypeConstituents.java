package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/** Reads the members of a written union or intersection type, flattening nested ones. */
final class TypeConstituents {

  static final String OPERATION = "unionOrIntersectionOrPrimaryType";
  static final String UNION = "|";
  static final String INTERSECTION = "&";

  private TypeConstituents() {}

  /** Returns the operator of a union or intersection node, or null for another type. */
  static String operatorOf(final SyntaxNode node) {
    if (!OPERATION.equals(node.kind())) {
      return null;
    }
    for (final SyntaxNode child : node.children()) {
      if (SyntaxText.isToken(child, UNION)) {
        return UNION;
      }
      if (SyntaxText.isToken(child, INTERSECTION)) {
        return INTERSECTION;
      }
    }
    return null;
  }

  /** Returns the constituents of a union or intersection node joined by {@code operator}. */
  static List<SyntaxNode> gather(final SyntaxNode node, final String operator) {
    final List<SyntaxNode> out = new ArrayList<>();
    collect(node, operator, out);
    return out;
  }

  private static void collect(
      final SyntaxNode node, final String operator, final List<SyntaxNode> out) {
    for (final SyntaxNode child : node.children()) {
      if (OPERATION.equals(child.kind()) && operator.equals(operatorOf(child))) {
        collect(child, operator, out);
      } else if (!SyntaxText.isToken(child, operator)) {
        out.add(child);
      }
    }
  }
}
