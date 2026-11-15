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
  private static final Set<String> DECISIONS = Set.of("if", "for", "while", "do", "case", "catch");
  private static final Set<String> BOOLEAN_OPERATORS = Set.of("&&", "||");
  private static final Set<String> CONDITIONS = Set.of("parExpression", "forControl");
  private static final int CONDITION_OF_TERNARY = 0;
  private static final int TERNARY_MARK = 1;

  private ClassMetrics() {}

  /**
   * Returns the cyclomatic complexity of a method or constructor: one plus its decision points. As
   * in PMD, {@code &&} and {@code ||} count only inside the condition of a statement or a ternary.
   */
  static int complexity(final SyntaxNode callable) {
    return 1 + decisions(callable, false);
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

  private static int decisions(final SyntaxNode node, final boolean condition) {
    int count = 0;
    final List<SyntaxNode> children = node.children();
    final boolean ternary = isTernary(node);
    for (int index = 0; index < children.size(); index++) {
      final SyntaxNode child = children.get(index);
      if (!MemberDeclarations.CLASS.equals(child.kind())) {
        final boolean inCondition =
            condition
                || CONDITIONS.contains(child.kind())
                || ternary && index == CONDITION_OF_TERNARY;
        count +=
            child.children().isEmpty()
                ? leafDecision(node, child, condition)
                : decisions(child, inCondition);
      }
    }
    return count;
  }

  private static boolean isTernary(final SyntaxNode node) {
    return isExpression(node)
        && node.children().size() > TERNARY_MARK
        && CONDITIONAL.equals(SyntaxWalk.text(node.children().get(TERNARY_MARK)));
  }

  private static int leafDecision(
      final SyntaxNode parent,
      final SyntaxNode leaf,
      final boolean condition
  ) {
    final String text = SyntaxWalk.text(leaf);
    final boolean conditional = CONDITIONAL.equals(text) && isExpression(parent);
    final boolean operator = condition && BOOLEAN_OPERATORS.contains(text);
    return DECISIONS.contains(text) || conditional || operator ? 1 : 0;
  }
}
