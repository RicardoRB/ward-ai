package dev.ward.analysis.frontend.antlr.typescript;

import java.util.Set;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

/**
 * Tells whether a {@code <} opens the type arguments of a call such as {@code f<T[]>(x)}. Like the
 * TypeScript compiler, it takes the {@code <} as type arguments when a balanced {@code <...>} made
 * only of tokens a type may hold is followed by {@code (}. The scan is bounded; a conditional type
 * at the top level of the arguments is read as a comparison.
 */
final class TypeArgumentsLookahead {

  /** The most tokens scanned, so a stray {@code <} costs little. */
  private static final int MAX_TOKENS = 256;

  /** Tokens that no type contains outside braces, parentheses and brackets. */
  private static final Set<Integer> NOT_IN_TYPE_ARGUMENTS =
      Set.of(
          TypeScriptParser.And,
          TypeScriptParser.Or,
          TypeScriptParser.Plus,
          TypeScriptParser.Multiply,
          TypeScriptParser.Divide,
          TypeScriptParser.Modulus,
          TypeScriptParser.Assign,
          TypeScriptParser.Equals_,
          TypeScriptParser.NotEquals,
          TypeScriptParser.IdentityEquals,
          TypeScriptParser.IdentityNotEquals,
          TypeScriptParser.PlusPlus,
          TypeScriptParser.MinusMinus,
          TypeScriptParser.Power,
          TypeScriptParser.NullCoalesce,
          TypeScriptParser.LessThanEquals,
          TypeScriptParser.GreaterThanEquals);

  /** Tokens that end a type argument list at the top level of a comparison. */
  private static final Set<Integer> NOT_AT_TOP_LEVEL =
      Set.of(TypeScriptParser.SemiColon, TypeScriptParser.QuestionMark);

  private TypeArgumentsLookahead() {}

  /**
   * Scans from the next token of the stream.
   *
   * @param input the token stream, positioned before the candidate {@code <}
   * @return {@code true} iff the next token is a {@code <} that opens type arguments of a call
   */
  static boolean opensCall(final TokenStream input) {
    return input.LT(1).getType() == TypeScriptParser.LessThan && balancedThenCall(input);
  }

  private static boolean balancedThenCall(final TokenStream input) {
    int angle = 0;
    int nesting = 0;
    for (int k = 1; k <= MAX_TOKENS; k++) {
      final int type = input.LT(k).getType();
      if (type == Token.EOF
          || NOT_IN_TYPE_ARGUMENTS.contains(type)
          || nesting == 0 && NOT_AT_TOP_LEVEL.contains(type)) {
        return false;
      }
      angle += angleDelta(type);
      nesting += nestingDelta(type);
      if (nesting < 0) {
        return false;
      }
      if (angle == 0) {
        return input.LT(k + 1).getType() == TypeScriptParser.OpenParen;
      }
    }
    return false;
  }

  private static int angleDelta(final int type) {
    if (type == TypeScriptParser.LessThan) {
      return 1;
    }
    return type == TypeScriptParser.MoreThan ? -1 : 0;
  }

  private static int nestingDelta(final int type) {
    if (type == TypeScriptParser.OpenParen
        || type == TypeScriptParser.OpenBracket
        || type == TypeScriptParser.OpenBrace) {
      return 1;
    }
    final boolean closes =
        type == TypeScriptParser.CloseParen
            || type == TypeScriptParser.CloseBracket
            || type == TypeScriptParser.CloseBrace;
    return closes ? -1 : 0;
  }
}
