package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads children, names and token text from the syntax tree of a Python file. */
final class PythonSyntax {

  /** The field that holds the text of a token node. */
  static final String TEXT = "text";

  /** The kind of a comment node, which is not part of the program. */
  static final String COMMENT_KIND = "comment";

  private static final String DOT = ".";

  /** The number of parts of a binary node such as an assignment, a walrus or a keyword argument. */
  static final int THREE_PARTS = 3;

  private PythonSyntax() {}

  /** The direct children of {@code node} whose kind is {@code kind}, in source order. */
  static List<SyntaxNode> children(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(child -> kind.equals(child.kind())).toList();
  }

  /** The text of a token node, or an empty string for a rule node. */
  static String text(final SyntaxNode node) {
    return String.valueOf(node.fields().getOrDefault(TEXT, ""));
  }

  /** The token nodes under {@code node} that carry text, comments excluded, in source order. */
  static List<SyntaxNode> tokens(final SyntaxNode node) {
    final List<SyntaxNode> tokens = new ArrayList<>();
    collectTokens(node, tokens);
    return tokens;
  }

  /** The dotted name under {@code node}: its name tokens joined with dots. */
  static String dottedName(final SyntaxNode node) {
    final List<String> parts = new ArrayList<>();
    for (final SyntaxNode token : tokens(node)) {
      if (!DOT.equals(text(token))) {
        parts.add(text(token));
      }
    }
    return String.join(DOT, parts);
  }

  private static void collectTokens(final SyntaxNode node, final List<SyntaxNode> tokens) {
    if (COMMENT_KIND.equals(node.kind())) {
      return;
    }
    if (node.children().isEmpty()) {
      if (node.fields().containsKey(TEXT)) {
        tokens.add(node);
      }
      return;
    }
    node.children().forEach(child -> collectTokens(child, tokens));
  }

  /** Whether {@code node} is a token with exactly {@code text}. */
  static boolean isToken(final SyntaxNode node, final String text) {
    return node.children().isEmpty() && text.equals(text(node));
  }

  /** The index of the first token with {@code text} among {@code parts}, or -1. */
  static int indexOfToken(final List<SyntaxNode> parts, final String text) {
    for (int index = 0; index < parts.size(); index++) {
      if (isToken(parts.get(index), text)) {
        return index;
      }
    }
    return -1;
  }

  /** The text of the first token under {@code node}, or an empty string when it has none. */
  static String firstText(final SyntaxNode node) {
    final List<SyntaxNode> tokens = tokens(node);
    return tokens.isEmpty() ? "" : text(tokens.get(0));
  }

  /** The indices of the tokens with {@code text} among {@code parts}, in order. */
  static List<Integer> indicesOfToken(final List<SyntaxNode> parts, final String text) {
    final List<Integer> indices = new ArrayList<>();
    for (int index = 0; index < parts.size(); index++) {
      if (isToken(parts.get(index), text)) {
        indices.add(index);
      }
    }
    return indices;
  }

  /** The first node of {@code kind} under {@code node} in source order, or empty when none is. */
  static Optional<SyntaxNode> firstOfKind(final SyntaxNode node, final String kind) {
    if (kind.equals(node.kind())) {
      return Optional.of(node);
    }
    for (final SyntaxNode child : node.children()) {
      final Optional<SyntaxNode> found = firstOfKind(child, kind);
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }
}
