package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/** Helpers split out of {@link TreeKit}. */
final class TreeText {
  private static final String COMMENT = "comment";
  private static final String ERROR = "error";
  private static final String TEXT = "text";

  private TreeText() {}

  /** Returns the tokens of a node without comments. */
  static List<String> leaves(final SyntaxNode node) {
    final List<String> out = new ArrayList<>();
    collect(node, out);
    return out;
  }

  static void collect(final SyntaxNode node, final List<String> out) {
    if (COMMENT.equals(node.kind()) || ERROR.equals(node.kind())) {
      return;
    }
    if (node.children().isEmpty()) {
      out.add(node.fields().getOrDefault(TEXT, ""));
      return;
    }
    node.children().forEach(child -> collect(child, out));
  }

  /** Returns the text of a node, its tokens joined without spaces. */
  static String text(final SyntaxNode node) {
    return String.join("", leaves(node));
  }

  /** Returns the text of the first identifier-like name under {@code node}, or empty. */
  static String name(final SyntaxNode node) {
    final List<String> leaves = leaves(node);
    return leaves.isEmpty() ? "" : leaves.get(0);
  }

  /** Returns the name of a variable declaration, or null when it declares a pattern. */
  static SyntaxNode variableName(final SyntaxNode declaration) {
    SyntaxNode name = TreeKit.child(declaration, "identifierOrKeyWord");
    if (name == null) {
      name = TreeKit.child(declaration, "assignable");
    }
    if (name == null) {
      return null;
    }
    return "identifier".equals(name.kind()) ? name : TreeKit.find(name, "identifier");
  }

  /** Removes the quotes of a string literal. */
  static String unquote(final String literal) {
    final boolean quoted =
        literal.length() >= 2
            && (literal.charAt(0) == '"' || literal.charAt(0) == '\'' || literal.charAt(0) == '`')
            && literal.charAt(literal.length() - 1) == literal.charAt(0);
    return quoted ? literal.substring(1, literal.length() - 1) : literal;
  }
}
