package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/** Reads the header of a TypeScript or JavaScript {@code for} loop. */
final class NodeForHeaders {

  private static final String KEYWORD_IN = "in";
  private static final String KEYWORD_OF = "of";
  private static final String SEMICOLON = ";";
  private static final int CLAUSE_COUNT = 3;

  private NodeForHeaders() {}

  /**
   * Reports whether the header is {@code x in xs} or {@code x of xs}. The {@code of} keyword is an
   * identifier in TypeScript and its own token in JavaScript, so its text decides in both.
   */
  static boolean isForEach(final List<SyntaxNode> header) {
    for (final SyntaxNode part : header) {
      final String token = NodeFlowSyntax.text(part);
      if (KEYWORD_IN.equals(token) || KEYWORD_OF.equals(token)) {
        return true;
      }
    }
    return false;
  }

  /** Returns the index of the first part whose text is the token. */
  static int indexOfToken(final List<SyntaxNode> parts, final String token) {
    for (int index = 0; index < parts.size(); index++) {
      if (token.equals(NodeFlowSyntax.text(parts.get(index)))) {
        return index;
      }
    }
    throw new IllegalStateException("expected " + token);
  }

  /** Splits a classic {@code for} header into its initialiser, condition and update clauses. */
  static List<List<SyntaxNode>> splitOnSemicolons(final List<SyntaxNode> header) {
    final List<List<SyntaxNode>> clauses = new ArrayList<>();
    int start = 0;
    for (int index = 0; index <= header.size(); index++) {
      if (index == header.size() || SEMICOLON.equals(NodeFlowSyntax.text(header.get(index)))) {
        clauses.add(List.copyOf(header.subList(start, index)));
        start = index + 1;
      }
    }
    if (clauses.size() != CLAUSE_COUNT) {
      throw new IllegalStateException("expected three clauses in a for header");
    }
    return clauses;
  }

  /**
   * Returns the loop variable of a for-in or for-of header: the part before the in or of keyword.
   */
  static SyntaxNode bindingOf(final List<SyntaxNode> header) {
    for (int index = 1; index < header.size(); index++) {
      final String token = NodeFlowSyntax.text(header.get(index));
      if (KEYWORD_IN.equals(token) || KEYWORD_OF.equals(token)) {
        return header.get(index - 1);
      }
    }
    throw new IllegalStateException("expected in or of in a for-each header");
  }

  /** Returns the last element of a non-empty list. */
  static SyntaxNode last(final List<SyntaxNode> nodes) {
    return nodes.get(nodes.size() - 1);
  }
}
