package dev.ward.analysis.frontend.antlr.javascript;

import java.util.Set;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

/** Tells from the next tokens which construct starts, for {@link QuickPrediction}. */
final class QuickTokens {
  private static final int SECOND = 2;
  private static final int THIRD = 3;

  /** A keyword followed by one of these is used as a name, as in {@code if.x} or {@code x = 1}. */
  private static final Set<Integer> AFTER_A_NAME =
      Set.of(JavaScriptParser.Assign, JavaScriptParser.Dot, JavaScriptParser.Colon);

  /** The tokens after {@code class} that make it a class expression, which has no name. */
  private static final Set<Integer> AFTER_ANONYMOUS_CLASS =
      Set.of(JavaScriptParser.OpenBrace, JavaScriptParser.Extends);

  /** The tokens that start the operand of a prefix operator. */
  private static final Set<Integer> OPERAND_START =
      Set.of(
          JavaScriptParser.Identifier,
          JavaScriptParser.This,
          JavaScriptParser.Super,
          JavaScriptParser.New,
          JavaScriptParser.StringLiteral,
          JavaScriptParser.DecimalLiteral,
          JavaScriptParser.HexIntegerLiteral,
          JavaScriptParser.BackTick,
          JavaScriptParser.RegularExpressionLiteral,
          JavaScriptParser.NullLiteral,
          JavaScriptParser.BooleanLiteral,
          JavaScriptParser.OpenParen,
          JavaScriptParser.OpenBracket,
          JavaScriptParser.OpenBrace,
          JavaScriptParser.Not,
          JavaScriptParser.BitNot,
          JavaScriptParser.Plus,
          JavaScriptParser.Minus,
          JavaScriptParser.Typeof,
          JavaScriptParser.Void,
          JavaScriptParser.Delete,
          JavaScriptParser.Await,
          JavaScriptParser.Function_,
          JavaScriptParser.Class,
          JavaScriptParser.Import,
          JavaScriptParser.Async
      );

  /** The tokens that go on with the operand of {@code new} after its first arguments. */
  private static final Set<Integer> CONTINUES_CHAIN =
      Set.of(
          JavaScriptParser.OpenParen,
          JavaScriptParser.OpenBracket,
          JavaScriptParser.Dot,
          JavaScriptParser.QuestionMarkDot
      );

  /** The most tokens read to find the parenthesis that closes the parameters of an arrow. */
  private static final int MAX_PARAMETER_TOKENS = 256;

  private QuickTokens() {}

  /** Tells whether the token after a keyword shows that the keyword is used as a name. */
  static boolean usedAsName(final TokenStream input) {
    return AFTER_A_NAME.contains(input.LA(SECOND));
  }

  /** Tells whether the second token starts the operand of a prefix operator. */
  static boolean operandFollows(final TokenStream input) {
    return OPERAND_START.contains(input.LA(SECOND));
  }

  /** Tells whether the tokens are {@code new Name(}. */
  static boolean newOfName(final TokenStream input) {
    return input.LA(SECOND) == JavaScriptParser.Identifier
        && input.LA(THIRD) == JavaScriptParser.OpenParen;
  }

  /** Tells whether the tokens are {@code new a.b(}, a path of one or more dots. */
  static boolean newOfPath(final TokenStream input) {
    int offset = SECOND;
    while (input.LA(offset) == JavaScriptParser.Identifier
        && input.LA(offset + 1) == JavaScriptParser.Dot) {
      offset += SECOND;
    }
    return offset > SECOND
        && input.LA(offset) == JavaScriptParser.Identifier
        && input.LA(offset + 1) == JavaScriptParser.OpenParen;
  }

  static boolean startsStatement(final int keyword, final TokenStream input) {
    return keyword != JavaScriptParser.Class
        || !AFTER_ANONYMOUS_CLASS.contains(input.LA(SECOND));
  }

  static boolean startsAsyncFunction(final TokenStream input) {
    final int second = input.LA(SECOND);
    if (second == JavaScriptParser.Function_) {
      return true;
    }
    if (second == JavaScriptParser.Identifier) {
      return input.LA(THIRD) == JavaScriptParser.ARROW;
    }
    return second == JavaScriptParser.OpenParen
        && closingParenthesis(input, SECOND, MAX_PARAMETER_TOKENS) > 0
        && input.LA(closingParenthesis(input, SECOND, MAX_PARAMETER_TOKENS) + 1)
        == JavaScriptParser.ARROW;
  }

  /**
   * Tells whether the arguments that start at the first token are not followed by more arguments:
   * {@code new a.b(1)(2)} gives the call to the operand, which only prediction knows.
   */
  static boolean callEndsOperand(final TokenStream input) {
    final int close = closingParenthesis(input, 1, Integer.MAX_VALUE);
    return close > 0 && !CONTINUES_CHAIN.contains(input.LA(close + 1));
  }

  /** Returns the offset of the parenthesis that closes the one at {@code open}, or -1. */
  private static int closingParenthesis(final TokenStream input, final int open, final int limit) {
    int depth = 0;
    for (int offset = open; offset - open < limit; offset++) {
      final int type = input.LA(offset);
      if (type == JavaScriptParser.OpenParen) {
        depth++;
      } else if (type == JavaScriptParser.CloseParen) {
        depth--;
        if (depth == 0) {
          return offset;
        }
      } else if (type == Token.EOF) {
        return -1;
      }
    }
    return -1;
  }
}
