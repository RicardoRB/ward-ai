package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Reads the shapes of expression nodes, which the grammar does not label. */
final class Exprs {
  static final String SINGLE = "singleExpression";
  static final String IDENTIFIER_NAME = "identifierName";
  private static final String SEQUENCE = "expressionSequence";
  private static final String OPEN_PAREN = "(";
  private static final Set<String> EXPRESSION_KINDS =
      Set.of(
          SINGLE,
          SEQUENCE,
          "literal",
          IDENTIFIER_NAME,
          "arrayLiteral",
          "objectLiteral",
          "anonymousFunction",
          "arrowFunctionDeclaration");

  private Exprs() {}

  /** Returns whether nodes of {@code kind} are expressions. */
  static boolean isExpression(final String kind) {
    return EXPRESSION_KINDS.contains(kind);
  }

  /** Returns the text of {@code node} when it is a single token, else empty. */
  static String token(final SyntaxNode node) {
    final List<String> leaves = TreeKit.leaves(node);
    return node.children().size() <= 1 && leaves.size() == 1 ? leaves.get(0) : "";
  }

  /** Returns the arguments of an {@code arguments} node or of a parenthesized expression. */
  static List<SyntaxNode> arguments(final SyntaxNode node) {
    final List<SyntaxNode> found = new ArrayList<>();
    for (final SyntaxNode part : TreeKit.parts(node)) {
      switch (part.kind()) {
        case "argumentList" -> TreeKit.children(part, "argument").forEach(found::add);
        case SEQUENCE -> found.addAll(TreeKit.children(part, SINGLE));
        case SINGLE -> found.add(part);
        default -> {
          // parentheses and commas
        }
      }
    }
    return found;
  }

  /** Returns the expression an {@code argument} node holds, or the node itself. */
  static SyntaxNode argumentValue(final SyntaxNode argument) {
    if (!"argument".equals(argument.kind())) {
      return argument;
    }
    final SyntaxNode value = TreeKit.child(argument, SINGLE);
    return value == null ? argument : value;
  }

  /** Returns whether the argument is a spread. */
  static boolean isSpread(final SyntaxNode argument) {
    return "argument".equals(argument.kind()) && TreeKit.hasToken(argument, "...");
  }

  /** Returns the first descendant expression when {@code node} wraps exactly one. */
  static SyntaxNode unwrap(final SyntaxNode node) {
    SyntaxNode current = node;
    while (SEQUENCE.equals(current.kind()) && TreeKit.parts(current).size() == 1) {
      current = TreeKit.parts(current).get(0);
    }
    return current;
  }

  /** Returns the name after a {@code .} or {@code ?.} in the parts of a member access, or null. */
  static String memberName(final List<SyntaxNode> parts) {
    for (int index = 1; index < parts.size() - 1; index++) {
      final String token = token(parts.get(index));
      if (".".equals(token) || "?.".equals(token)) {
        final SyntaxNode name = parts.get(index + 1);
        return isName(name) ? TreeKit.text(name) : null;
      }
    }
    return null;
  }

  private static boolean isName(final SyntaxNode node) {
    final String text = TreeKit.text(node);
    return !text.isEmpty()
        && Character.isJavaIdentifierStart(text.charAt(0))
        && text.chars().allMatch(Character::isJavaIdentifierPart)
        && (IDENTIFIER_NAME.equals(node.kind())
            || node.children().size() == 1
            || TreeKit.parts(node).size() == 1);
  }

  /** Returns whether the parts open with a parenthesis token. */
  static boolean isParenthesized(final List<SyntaxNode> parts) {
    return !parts.isEmpty() && OPEN_PAREN.equals(token(parts.get(0)));
  }

  /** Returns whether the member access in {@code parts} is optional ({@code ?.}). */
  static boolean isOptional(final List<SyntaxNode> parts) {
    return parts.stream().anyMatch(part -> "?.".equals(token(part)))
        || parts.stream().anyMatch(part -> "?".equals(token(part)))
            && parts.stream().anyMatch(part -> ".".equals(token(part)));
  }
}
