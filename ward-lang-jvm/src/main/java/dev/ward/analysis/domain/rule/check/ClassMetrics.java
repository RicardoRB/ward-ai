package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Set;

/**
 * Measures the methods of a class that the class-level design checks read: the cyclomatic
 * complexity of each method, and which methods are accessors.
 */
final class ClassMetrics {

  private static final String EXPRESSION = "expression";
  private static final String IDENTIFIER = "identifier";
  private static final String FORMAL_PARAMETER = "formalParameter";
  private static final String GETTER = "get";
  private static final String BOOLEAN_GETTER = "is";
  private static final String SETTER = "set";
  private static final String CONDITIONAL = "?";
  private static final Set<String> DECISIONS =
      Set.of("if", "for", "while", "do", "case", "catch", "&&", "||");

  private ClassMetrics() {}

  /** Returns the cyclomatic complexity of a method or constructor: one plus its decision points. */
  static int complexity(final SyntaxNode callable) {
    return 1 + decisions(callable);
  }

  /** Tells whether a method is a getter, an is-method with no parameters, or a setter. */
  static boolean isAccessor(final SyntaxNode method) {
    final String name = MemberDeclarations.name(method);
    final int parameters = parameterCount(method);
    return (name.startsWith(GETTER) || name.startsWith(BOOLEAN_GETTER)) && parameters == 0
        || name.startsWith(SETTER) && parameters == 1;
  }

  /** Returns the number of formal parameters a method or constructor declares. */
  static int parameterCount(final SyntaxNode callable) {
    return (int)
        SyntaxWalk.descendants(callable).stream()
            .filter(node -> FORMAL_PARAMETER.equals(node.kind()))
            .count();
  }

  /** Returns the identifiers below a node, as their text. */
  static List<String> identifiers(final SyntaxNode node) {
    return SyntaxWalk.descendants(node).stream()
        .filter(child -> IDENTIFIER.equals(child.kind()))
        .map(SyntaxWalk::text)
        .toList();
  }

  /** Tells whether a node is an expression. */
  static boolean isExpression(final SyntaxNode node) {
    return EXPRESSION.equals(node.kind());
  }

  private static int decisions(final SyntaxNode node) {
    int count = 0;
    for (final SyntaxNode child : node.children()) {
      if (!MemberDeclarations.CLASS.equals(child.kind())) {
        count += child.children().isEmpty() ? leafDecision(node, child) : decisions(child);
      }
    }
    return count;
  }

  private static int leafDecision(final SyntaxNode parent, final SyntaxNode leaf) {
    final String text = SyntaxWalk.text(leaf);
    final boolean conditional = CONDITIONAL.equals(text) && isExpression(parent);
    return DECISIONS.contains(text) || conditional ? 1 : 0;
  }
}
