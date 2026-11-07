package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/**
 * The tokens of a syntax tree in source order, each with the kind of its parent and its index, so a
 * check can look at the token before or after one. Comments are not tokens.
 */
final class TokenSequence {

  private static final String LITERAL_TOKEN_PREFIX = "'";

  private final List<Token> tokens;

  private TokenSequence(final List<Token> tokens) {
    this.tokens = List.copyOf(tokens);
  }

  /** Builds the sequence of the tokens below {@code root}. */
  static TokenSequence of(final SyntaxNode root) {
    final List<Token> tokens = new ArrayList<>();
    collect(root, "", tokens);
    return new TokenSequence(tokens);
  }

  private static void collect(
      final SyntaxNode node, final String parentKind, final List<Token> tokens) {
    if (node.children().isEmpty()) {
      if (node.fields().containsKey("text") && !SyntaxWalk.COMMENT_KIND.equals(node.kind())) {
        tokens.add(new Token(node, parentKind, tokens.size()));
      }
      return;
    }
    node.children().forEach(child -> collect(child, node.kind(), tokens));
  }

  /** The tokens in source order. */
  List<Token> all() {
    return tokens;
  }

  /** The token after {@code token}, or {@code null} when it is the last one. */
  SyntaxNode after(final Token token) {
    return token.index() + 1 < tokens.size() ? tokens.get(token.index() + 1).node() : null;
  }

  /** The token before {@code token}, or {@code null} when it is the first one. */
  SyntaxNode before(final Token token) {
    return token.index() > 0 ? tokens.get(token.index() - 1).node() : null;
  }

  /**
   * A token with the kind of its parent.
   *
   * @param node the token
   * @param parentKind the kind of the node that holds the token
   * @param index the position of the token in the sequence
   */
  record Token(SyntaxNode node, String parentKind, int index) {}

  /** Tells whether a kind names a literal token such as {@code '{'}. */
  static boolean isLiteral(final String kind) {
    return kind.startsWith(LITERAL_TOKEN_PREFIX);
  }
}
