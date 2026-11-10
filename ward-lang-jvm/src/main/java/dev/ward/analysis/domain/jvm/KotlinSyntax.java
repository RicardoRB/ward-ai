package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/** Reads the Kotlin syntax nodes that the scans share: children by kind and dotted names. */
final class KotlinSyntax {
  private static final String SIMPLE_IDENTIFIER = "simpleIdentifier";
  private static final String SIMPLE_USER_TYPE = "simpleUserType";
  private static final String ATOMIC = "atomicExpression";
  private static final String POSTFIX_OPERATION = "postfixUnaryOperation";
  private static final String CALL_SUFFIX = "callSuffix";
  private static final String DOT = ".";

  private KotlinSyntax() {}

  /** Returns the direct children of {@code node} of {@code kind}, in source order. */
  static List<SyntaxNode> children(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(child -> kind.equals(child.kind())).toList();
  }

  /** Returns the simple names of a dotted identifier, joined with dots. */
  static String segments(final SyntaxNode node) {
    final List<String> parts = new ArrayList<>();
    for (final SyntaxNode child : children(node, SIMPLE_IDENTIFIER)) {
      parts.add(JavaSyntax.dotted(child));
    }
    return String.join(DOT, parts);
  }

  /** Returns the dotted name of a user type, without its type arguments. */
  static String userTypeName(final SyntaxNode node) {
    final List<String> parts = new ArrayList<>();
    for (final SyntaxNode simple : children(node, SIMPLE_USER_TYPE)) {
      parts.add(segments(simple));
    }
    return String.join(DOT, parts);
  }

  /** Returns whether a postfix expression is a call of its leading name, as in {@code f(x)}. */
  static boolean startsWithCall(final SyntaxNode node) {
    return node.children().stream()
        .filter(child -> POSTFIX_OPERATION.equals(child.kind()))
        .findFirst()
        .filter(operation -> !children(operation, CALL_SUFFIX).isEmpty())
        .isPresent();
  }

  /** Returns the simple name at the start of a postfix expression, or an empty string. */
  static String atomName(final SyntaxNode node) {
    return children(node, ATOMIC).stream()
        .flatMap(atom -> children(atom, SIMPLE_IDENTIFIER).stream())
        .map(JavaSyntax::dotted)
        .findFirst()
        .orElse("");
  }
}
