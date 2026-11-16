package dev.ward.analysis.frontend.antlr.swift;

import java.util.Set;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

/**
 * Predicates that settle, from the tokens alone, the choices of the Swift grammar that are
 * ambiguous without them. A choice the parser cannot settle from the tokens makes it read on to
 * the end of the construct with its slowest prediction, once per nesting level, so a long test
 * file took minutes (#567). None of these predicates reads the parser's rule context, so the parser
 * can evaluate them while it predicts.
 */
final class SwiftLookahead {
  private static final String NEWLINE = "\n";
  /** The tokens after which the first statement of a block may stand on the same line. */
  private static final Set<String> BLOCK_OPENERS = Set.of("{", ";", ":", "in");

  /** The keywords that start a branch statement, which is never read as an expression first. */
  private static final Set<String> BRANCH_KEYWORDS = Set.of("if", "switch");

  /** The modifiers that are also names, so only what follows tells a declaration from a use. */
  private static final Set<String> MODIFIERS =
      Set.of(
          "final",
          "open",
          "lazy",
          "weak",
          "unowned",
          "dynamic",
          "optional",
          "override",
          "required",
          "convenience",
          "mutating",
          "nonmutating",
          "prefix",
          "postfix",
          "infix",
          "indirect"
      );

  private SwiftLookahead() {}

  /**
   * Whether a statement may start at the next token: it is the first of the file or of a block, or
   * a line break or a semicolon separates it from the statement before.
   */
  static boolean isStatementStart(final TokenStream tokens) {
    final Token previous = previous(tokens, tokens.index());
    return previous == null
        || isLineBreakBefore(tokens)
        || BLOCK_OPENERS.contains(previous.getText());
  }

  /**
   * Whether the next token is on the line of the token before it. A parenthesis or a bracket that
   * starts a line starts a statement and does not continue the expression of the line above.
   */
  static boolean isOnSameLine(final TokenStream tokens) {
    return !isLineBreakBefore(tokens);
  }

  /**
   * Whether an expression statement can start at the next token: not at {@code if} or {@code
   * switch}, which start a statement, and not at a modifier that a declaration follows, as in
   * {@code final class}.
   */
  static boolean isExpressionStatement(final TokenStream tokens) {
    final String text = tokens.LT(1).getText();
    if (BRANCH_KEYWORDS.contains(text)) {
      return false;
    }
    return !(MODIFIERS.contains(text) && Character.isLetter(tokens.LT(2).getText().charAt(0)));
  }

  /**
   * Whether the {@code #if} at the next token guards members of the expression before it: the
   * first token after the line of the condition is a dot.
   */
  static boolean isPostfixDirective(final TokenStream tokens) {
    if (!"#if".equals(tokens.LT(1).getText())) {
      return false;
    }
    boolean lineEnded = false;
    for (int i = tokens.index() + 1; i < tokens.size(); i++) {
      final Token token = tokens.get(i);
      if (token.getChannel() != Token.DEFAULT_CHANNEL) {
        lineEnded = lineEnded || token.getText().contains(NEWLINE);
      } else if (lineEnded) {
        return token.getType() == SwiftParser.DOT;
      }
    }
    return false;
  }

  /** Whether nothing, not even a space, stands between the next token and the one before it. */
  static boolean isAttached(final TokenStream tokens) {
    final int index = tokens.index();
    return index > 0 && tokens.get(index - 1).getChannel() == Token.DEFAULT_CHANNEL;
  }

  /** Whether the {@code try} at the next token has no {@code ?} or {@code !} attached to it. */
  static boolean isPlainTry(final TokenStream tokens) {
    final int after = tokens.index() + 1;
    if (after >= tokens.size()) {
      return true;
    }
    final int type = tokens.get(after).getType();
    return type != SwiftParser.BANG && type != SwiftParser.QUESTION;
  }

  static boolean isLineBreakBefore(final TokenStream tokens) {
    for (int i = tokens.index() - 1; i >= 0; i--) {
      final Token token = tokens.get(i);
      if (token.getChannel() == Token.DEFAULT_CHANNEL) {
        return false;
      }
      if (token.getText().contains(NEWLINE)) {
        return true;
      }
    }
    return false;
  }

  /** Returns the token before {@code index} that is not hidden, or {@code null} at the start. */
  private static Token previous(final TokenStream tokens, final int index) {
    final int before = previousIndex(tokens, index);
    return before < 0 ? null : tokens.get(before);
  }

  private static int previousIndex(final TokenStream tokens, final int index) {
    int before = index - 1;
    while (before >= 0 && tokens.get(before).getChannel() != Token.DEFAULT_CHANNEL) {
      before--;
    }
    return before;
  }
}
