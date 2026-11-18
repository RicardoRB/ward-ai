package dev.ward.analysis.frontend.antlr.typescript;

import java.util.Set;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

/** Scans ahead over balanced tokens to settle a decision for {@link QuickPrediction}. */
final class QuickScans {
  private static final int SECOND = 2;

  /** The tokens that go on with the operand of {@code new} after its first arguments. */
  private static final Set<Integer> CONTINUES_CHAIN =
      Set.of(
          TypeScriptParser.OpenParen,
          TypeScriptParser.OpenBracket,
          TypeScriptParser.Dot,
          TypeScriptParser.QuestionMarkDot,
          TypeScriptParser.LessThan
      );

  /** The tokens that open a bracket pair inside a type. */
  private static final Set<Integer> OPENS_IN_TYPE =
      Set.of(
          TypeScriptParser.OpenParen,
          TypeScriptParser.OpenBracket,
          TypeScriptParser.OpenBrace,
          TypeScriptParser.LessThan
      );

  /** The tokens that close a bracket pair inside a type. */
  private static final Set<Integer> CLOSES_IN_TYPE =
      Set.of(
          TypeScriptParser.CloseParen,
          TypeScriptParser.CloseBracket,
          TypeScriptParser.CloseBrace,
          TypeScriptParser.MoreThan
      );

  /** The tokens that start the name of a class member that is written as one token. */
  private static final Set<Integer> MEMBER_NAME =
      Set.of(
          TypeScriptParser.Identifier,
          TypeScriptParser.StringLiteral,
          TypeScriptParser.DecimalLiteral
      );

  /** A brace after one of these starts an object type, not a body. */
  private static final Set<Integer> BEFORE_OBJECT_TYPE =
      Set.of(
          TypeScriptParser.Colon,
          TypeScriptParser.BitOr,
          TypeScriptParser.BitAnd,
          TypeScriptParser.ARROW,
          TypeScriptParser.Extends,
          TypeScriptParser.QuestionMark,
          TypeScriptParser.Typeof,
          TypeScriptParser.KeyOf,
          TypeScriptParser.ReadOnly,
          TypeScriptParser.Infer,
          TypeScriptParser.Unique,
          TypeScriptParser.Comma
      );

  /** The tokens that continue a type after its first part. */
  private static final Set<Integer> CONTINUES_TYPE =
      Set.of(
          TypeScriptParser.Extends,
          TypeScriptParser.BitOr,
          TypeScriptParser.BitAnd,
          TypeScriptParser.OpenBracket
      );

  private QuickScans() {}

  /**
   * Tells whether the type starts with an object type that nothing continues: not a conditional
   * type ({@code extends}), a union or intersection, or an array type.
   */
  static boolean objectTypeEndsType(final TokenStream input) {
    if (input.LA(1) != TypeScriptParser.OpenBrace) {
      return false;
    }
    int depth = 0;
    for (int offset = 1; input.LA(offset) != Token.EOF; offset++) {
      final int type = input.LA(offset);
      depth += type == TypeScriptParser.OpenBrace ? 1 : type == TypeScriptParser.CloseBrace ? -1 : 0;
      if (depth == 0) {
        return !CONTINUES_TYPE.contains(input.LA(offset + 1));
      }
    }
    return false;
  }

  /**
   * Tells whether the tokens are {@code async (...)}, a return type and {@code =>}. A call of
   * {@code async} in the first branch of a conditional looks the same, so the caller checks that.
   */
  static boolean startsAsyncArrowWithReturnType(final TokenStream input) {
    if (input.LA(SECOND) != TypeScriptParser.OpenParen) {
      return false;
    }
    final int close = closingParenthesis(input, SECOND, QuickTokens.MAX_PARAMETER_TOKENS);
    return close > 0
        && input.LA(close + 1) == TypeScriptParser.Colon
        && arrowAfterType(input, close + SECOND);
  }

  /**
   * Tells whether the tokens are the modifiers of a class member, {@code get} or {@code set}, a
   * name, a parameter list, and then the body of the accessor: a brace, or a return type and a
   * brace. Without the body the declaration is the signature of an accessor, which has no body.
   */
  static boolean startsAccessor(final TokenStream input) {
    int offset = 1;
    while (QuickTokens.MEMBER_MODIFIERS.contains(input.LA(offset))) {
      offset++;
    }
    final int keyword = input.LA(offset);
    if ((keyword != TypeScriptParser.Get && keyword != TypeScriptParser.Set)
        || !MEMBER_NAME.contains(input.LA(offset + 1))
        || input.LA(offset + SECOND) != TypeScriptParser.OpenParen) {
      return false;
    }
    final int close = closingParenthesis(input, offset + SECOND, QuickTokens.MAX_PARAMETER_TOKENS);
    final int after = close > 0 ? input.LA(close + 1) : Token.EOF;
    return after == TypeScriptParser.OpenBrace
        || after == TypeScriptParser.Colon && braceAfterType(input, close + SECOND);
  }

  /**
   * Tells whether the arguments that start at the first token are not followed by more arguments or
   * type arguments: {@code new a.b(1)(2)} gives the call to the operand, which only prediction knows.
   */
  static boolean callEndsOperand(final TokenStream input) {
    final int close = closingParenthesis(input, 1, Integer.MAX_VALUE);
    return close > 0 && !CONTINUES_CHAIN.contains(input.LA(close + 1));
  }

  /** Returns the offset of the parenthesis that closes the one at {@code open} within {@code limit} tokens, or -1. */
  private static int closingParenthesis(final TokenStream input, final int open, final int limit) {
    int depth = 0;
    for (int offset = open; offset - open < limit; offset++) {
      final int type = input.LA(offset);
      if (type == TypeScriptParser.OpenParen) {
        depth++;
      } else if (type == TypeScriptParser.CloseParen) {
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

  /** Tells whether the type that starts at {@code from} ends in {@code =>} at its top level. */
  private static boolean arrowAfterType(final TokenStream input, final int from) {
    return endOfType(input, from, TypeScriptParser.ARROW);
  }

  /** Tells whether the type that starts at {@code from} is followed by a brace. */
  private static boolean braceAfterType(final TokenStream input, final int from) {
    return endOfType(input, from, TypeScriptParser.OpenBrace);
  }

  /**
   * Reads a type and tells whether {@code stop} comes at its top level, after a token that can end
   * a type: a brace after {@code |} or {@code :} opens an object type, not a body.
   */
  private static boolean endOfType(final TokenStream input, final int from, final int stop) {
    int depth = 0;
    for (int offset = from; offset < from + QuickTokens.MAX_PARAMETER_TOKENS; offset++) {
      final int type = input.LA(offset);
      if (depth == 0 && reaches(input, offset, stop)) {
        return true;
      }
      depth += OPENS_IN_TYPE.contains(type) ? 1 : CLOSES_IN_TYPE.contains(type) ? -1 : 0;
      if (depth < 0 || type == Token.EOF || depth == 0 && type == TypeScriptParser.SemiColon) {
        return false;
      }
    }
    return false;
  }

  private static boolean reaches(final TokenStream input, final int offset, final int stop) {
    return input.LA(offset) == stop
        && (stop != TypeScriptParser.OpenBrace || endsType(input, offset));
  }

  private static boolean endsType(final TokenStream input, final int offset) {
    return !BEFORE_OBJECT_TYPE.contains(input.LA(offset - 1));
  }
}
