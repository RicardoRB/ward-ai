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
  private static final String ASSIGN = "=";

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

  /** The tokens that end a pattern made of one name. */
  private static final Set<String> AFTER_NAME = Set.of(":", ",", ")", "in", "{", "where", "as");

  /** The tokens that end a pattern made of one enumeration case. */
  private static final Set<String> AFTER_CASE = Set.of(":", ",", ")", "{", "where");

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
   * Whether the pattern at the next token can be an expression. One name, or one enumeration case
   * without a payload, is a binding or a case pattern.
   */
  static boolean isExpressionPattern(final TokenStream tokens) {
    final Token first = tokens.LT(1);
    if (first.getType() == SwiftParser.Identifier) {
      return !endsPattern(tokens, 2, AFTER_NAME);
    }
    final boolean enumCase =
        first.getType() == SwiftParser.DOT && tokens.LT(2).getType() == SwiftParser.Identifier;
    return !(enumCase && endsPattern(tokens, 2 + 1, AFTER_CASE));
  }

  /**
   * Whether the bracket at the next token opens a subscript whose first argument has a label, as
   * in {@code counts[key, default: 0]} or {@code table[row: 2]}.
   */
  static boolean isLabeledSubscript(final TokenStream tokens) {
    if (isLineBreakBefore(tokens)) {
      return false;
    }
    int depth = 0;
    for (int ahead = 1; tokens.LA(ahead) != Token.EOF; ahead++) {
      final String text = tokens.LT(ahead).getText();
      depth -= depthChange(text);
      if (depth == 0) {
        return false;
      }
      if (depth == 1 && isLabelStart(tokens, ahead)) {
        return true;
      }
    }
    return false;
  }

  /** Whether a label and its colon follow the bracket or the comma {@code ahead} positions on. */
  private static boolean isLabelStart(final TokenStream tokens, final int ahead) {
    final String text = tokens.LT(ahead).getText();
    final boolean separator = "[".equals(text) || ",".equals(text);
    return separator
        && ":".equals(tokens.LT(ahead + 2).getText())
        && Character.isLetter(tokens.LT(ahead + 1).getText().charAt(0));
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

  /** Whether the token {@code ahead} positions on ends a pattern: a listed token or one {@code =}. */
  private static boolean endsPattern(
      final TokenStream tokens,
      final int ahead,
      final Set<String> enders
  ) {
    final String text = tokens.LT(ahead).getText();
    if (ASSIGN.equals(text)) {
      return !ASSIGN.equals(tokens.LT(ahead + 1).getText());
    }
    return enders.contains(text);
  }

  private static boolean isOpening(final String text) {
    return "(".equals(text) || "[".equals(text) || "{".equals(text);
  }

  private static int depthChange(final String text) {
    if (")".equals(text) || "]".equals(text) || "}".equals(text)) {
      return 1;
    }
    return isOpening(text) ? -1 : 0;
  }

  private static boolean isLineBreakBefore(final TokenStream tokens) {
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
