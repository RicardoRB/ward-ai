package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Tells whether a literal belongs to the initializer of a constant, as Checkstyle's {@code
 * MagicNumber} does: the nearest variable definition around it is {@code final}, an interface
 * constant or an enum constant, and only the nodes that Checkstyle waives lie between them: casts,
 * calls, object creation, array initializers, unary signs, binary and ternary operators and
 * assignments. A literal in a lambda, an array size, an index or a negation is not waived.
 */
final class ConstantContext {

  private static final String LOCAL = "localVariableDeclaration";
  private static final String MEMBER = "classBodyDeclaration";
  private static final Set<String> DECLARATIONS =
      Set.of("variableDeclarator", "constantDeclarator", "enumConstant", LOCAL);
  private static final Set<String> IMPLICIT_CONSTANTS =
      Set.of("constantDeclarator", "enumConstant");
  private static final Set<String> WAIVED_KINDS =
      Set.of(
          "primary",
          "methodCall",
          "arguments",
          "expressionList",
          "creator",
          "classCreatorRest",
          "arrayInitializer",
          "variableInitializer",
          "typeType",
          "literal",
          "integerLiteral",
          "floatLiteral");
  private static final String EXPRESSION = "expression";
  private static final String FINAL = "final";
  private static final Set<String> BLOCKING_OPERATORS = Set.of("'['", "'!'", "'~'", "'++'", "'--'");
  private static final int PREFIX_PARTS = 2;

  private ConstantContext() {}

  /** Whether the literal, with these ancestors (root first), is waived as part of a constant. */
  static boolean waived(final List<SyntaxNode> up) {
    final int declaration = nearestDeclaration(up);
    if (declaration < 0 || !isConstant(up, declaration)) {
      return false;
    }
    return up.subList(declaration + 1, up.size()).stream().allMatch(ConstantContext::passes);
  }

  private static int nearestDeclaration(final List<SyntaxNode> up) {
    for (int index = up.size() - 1; index >= 0; index--) {
      if (DECLARATIONS.contains(up.get(index).kind())) {
        return index;
      }
    }
    return -1;
  }

  private static boolean isConstant(final List<SyntaxNode> up, final int declaration) {
    final SyntaxNode node = up.get(declaration);
    if (IMPLICIT_CONSTANTS.contains(node.kind())) {
      return true;
    }
    if (LOCAL.equals(node.kind())) {
      return hasFinal(node);
    }
    return fieldOwner(up, declaration).map(ConstantContext::hasFinal).orElse(false);
  }

  /** The member or local declaration whose modifiers decide whether a declarator is final. */
  private static Optional<SyntaxNode> fieldOwner(final List<SyntaxNode> up, final int declarator) {
    for (int index = declarator - 1; index >= 0; index--) {
      final String kind = up.get(index).kind();
      if (LOCAL.equals(kind)) {
        return Optional.of(up.get(index));
      }
      if (MEMBER.equals(kind)) {
        return Optional.of(up.get(index));
      }
    }
    return Optional.empty();
  }

  private static boolean hasFinal(final SyntaxNode owner) {
    return owner.children().stream()
        .filter(child -> "modifier".equals(child.kind()) || "variableModifier".equals(child.kind()))
        .anyMatch(child -> FINAL.equals(SyntaxWalk.text(child)));
  }

  /** Whether a node between the literal and the declaration is one that Checkstyle waives. */
  private static boolean passes(final SyntaxNode node) {
    if (WAIVED_KINDS.contains(node.kind())) {
      return true;
    }
    if (!EXPRESSION.equals(node.kind())) {
      return false;
    }
    final List<SyntaxNode> parts = node.children();
    return parts.size() < 2
        || !BLOCKING_OPERATORS.contains(parts.get(parts.size() == PREFIX_PARTS ? 0 : 1).kind());
  }
}
