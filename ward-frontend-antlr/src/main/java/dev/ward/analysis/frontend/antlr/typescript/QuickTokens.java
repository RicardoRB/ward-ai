package dev.ward.analysis.frontend.antlr.typescript;

import java.util.Set;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

/** Tells from the next tokens which construct starts, for {@link QuickPrediction}. */
final class QuickTokens {
  private static final int SECOND = 2;
  private static final int THIRD = 3;
  private static final int FOURTH = 4;

  /** A keyword followed by one of these is used as a name, as in {@code if.x} or {@code x = 1}. */
  private static final Set<Integer> AFTER_A_NAME =
      Set.of(TypeScriptParser.Assign, TypeScriptParser.Dot, TypeScriptParser.Colon);

  /** The tokens that start a binding after {@code let}. */
  private static final Set<Integer> AFTER_LET =
      Set.of(TypeScriptParser.Identifier, TypeScriptParser.OpenBracket, TypeScriptParser.OpenBrace);

  /** The tokens after {@code class} that make it a class expression, which has no name. */
  private static final Set<Integer> AFTER_ANONYMOUS_CLASS =
      Set.of(
          TypeScriptParser.OpenBrace,
          TypeScriptParser.Extends,
          TypeScriptParser.Implements,
          TypeScriptParser.LessThan
      );

  /** The tokens that start the operand of a prefix operator. */
  private static final Set<Integer> OPERAND_START =
      Set.of(
          TypeScriptParser.Identifier,
          TypeScriptParser.This,
          TypeScriptParser.Super,
          TypeScriptParser.New,
          TypeScriptParser.StringLiteral,
          TypeScriptParser.DecimalLiteral,
          TypeScriptParser.HexIntegerLiteral,
          TypeScriptParser.BackTick,
          TypeScriptParser.RegularExpressionLiteral,
          TypeScriptParser.NullLiteral,
          TypeScriptParser.BooleanLiteral,
          TypeScriptParser.OpenParen,
          TypeScriptParser.OpenBracket,
          TypeScriptParser.OpenBrace,
          TypeScriptParser.Not,
          TypeScriptParser.BitNot,
          TypeScriptParser.Plus,
          TypeScriptParser.Minus,
          TypeScriptParser.Typeof,
          TypeScriptParser.Void,
          TypeScriptParser.Delete,
          TypeScriptParser.Await,
          TypeScriptParser.Function_,
          TypeScriptParser.Class,
          TypeScriptParser.Import,
          TypeScriptParser.Async
      );

  /** The keywords of the declarations that {@code export} may precede. */
  private static final Set<Integer> EXPORTED_DECLARATION =
      Set.of(
          TypeScriptParser.Function_,
          TypeScriptParser.Async,
          TypeScriptParser.Const,
          TypeScriptParser.Let,
          TypeScriptParser.Var,
          TypeScriptParser.Class,
          TypeScriptParser.Abstract,
          TypeScriptParser.Interface,
          TypeScriptParser.Enum,
          TypeScriptParser.Declare,
          TypeScriptParser.Namespace
      );

  /** The modifiers that may precede the name of a class member. */
  private static final Set<Integer> MEMBER_MODIFIERS =
      Set.of(
          TypeScriptParser.Public,
          TypeScriptParser.Private,
          TypeScriptParser.Protected,
          TypeScriptParser.Static,
          TypeScriptParser.Override,
          TypeScriptParser.Async
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
    return input.LA(SECOND) == TypeScriptParser.Identifier
        && input.LA(THIRD) == TypeScriptParser.OpenParen;
  }

  static boolean exportsDeclaration(final TokenStream input) {
    return input.LA(1) == TypeScriptParser.Export
        && EXPORTED_DECLARATION.contains(input.LA(SECOND))
        && !AFTER_A_NAME.contains(input.LA(THIRD));
  }

  /** Tells whether the tokens are the modifiers of a class member, a plain name and a {@code (}. */
  static boolean startsMethod(final TokenStream input) {
    int offset = 1;
    while (MEMBER_MODIFIERS.contains(input.LA(offset))) {
      offset++;
    }
    return input.LA(offset) == TypeScriptParser.Identifier
        && input.LA(offset + 1) == TypeScriptParser.OpenParen;
  }

  static boolean startsStatement(final int keyword, final TokenStream input) {
    final int after = input.LA(SECOND);
    if (keyword == TypeScriptParser.Let) {
      return AFTER_LET.contains(after);
    }
    if (keyword == TypeScriptParser.Const) {
      return after != TypeScriptParser.Enum;
    }
    return keyword != TypeScriptParser.Class || !AFTER_ANONYMOUS_CLASS.contains(after);
  }

  static boolean exportedDefaultClass(final TokenStream input) {
    return input.LA(SECOND) == TypeScriptParser.Default
        && input.LA(THIRD) == TypeScriptParser.Class
        && !AFTER_ANONYMOUS_CLASS.contains(input.LA(FOURTH));
  }

  static boolean startsAsyncFunction(final TokenStream input) {
    final int second = input.LA(SECOND);
    if (second == TypeScriptParser.Function_) {
      return true;
    }
    if (second == TypeScriptParser.Identifier) {
      return input.LA(THIRD) == TypeScriptParser.ARROW;
    }
    return second == TypeScriptParser.OpenParen && arrowAfterParenthesis(input);
  }

  /** Tells whether the parenthesis at the second token closes directly before {@code =>}. */
  private static boolean arrowAfterParenthesis(final TokenStream input) {
    int depth = 0;
    for (int offset = SECOND; offset < MAX_PARAMETER_TOKENS; offset++) {
      final int type = input.LA(offset);
      if (type == TypeScriptParser.OpenParen) {
        depth++;
      } else if (type == TypeScriptParser.CloseParen) {
        depth--;
      }
      if (depth == 0 || type == Token.EOF) {
        return input.LA(offset + 1) == TypeScriptParser.ARROW;
      }
    }
    return false;
  }
}
