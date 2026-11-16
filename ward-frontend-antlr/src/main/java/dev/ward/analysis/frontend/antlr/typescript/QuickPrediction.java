package dev.ward.analysis.frontend.antlr.typescript;

import java.util.Set;
import org.antlr.v4.runtime.TokenStream;

/**
 * Decides four decisions of the TypeScript grammar from one or two tokens, where the adaptive
 * prediction of ANTLR would read to the end of the statement. Statements end without a semicolon,
 * so the grammar lets {@code f} and {@code (x)} be two statements, and every keyword is also a
 * name: prediction then follows both readings of {@code f(...)}, {@code await f()}, {@code try
 * {...}} or {@code const a = ...} for hundreds of tokens and finds them ambiguous, and the first
 * alternative wins. This class returns that first alternative at once, in the cases where no valid
 * program has the other reading:
 *
 * <ul>
 *   <li>after an expression, a {@code .}, and a {@code (} or {@code [} on the same
 *       line, continue the expression when the precedence allows the member access or the call,
 *       except for a {@code (} after the operand of {@code new};
 *   <li>a statement that starts with a reserved statement keyword is that statement;
 *   <li>{@code await}, {@code typeof}, {@code void} and {@code delete} before the start of an
 *       operand are operators;
 *   <li>a {@code <} after the name of a type opens its type arguments;
 *   <li>{@code async} before {@code function}, before {@code x =>} or before a parenthesis that
 *       closes in front of {@code =>} starts a function, and {@code new Name(} is the first form
 *       of {@code new};
 *   <li>{@code export default class Name} is a class declaration, and {@code export} before the
 *       keyword of a declaration belongs to the declaration that follows;
 *   <li>in a class, {@code name(} after any modifiers starts a method, and a {@code {} after the
 *       signature of a method or a constructor opens its body.
 * </ul>
 *
 * <p>{@link QuickDecisions} reads the decision numbers and alternatives from the ATN, and {@link
 * QuickTokens} reads the tokens; {@code QuickPredictionTest} checks that every decision is found.
 */
final class QuickPrediction {
  /** No quick answer: the adaptive prediction decides. */
  static final int UNDECIDED = AtnPaths.NONE;

  private static final int ENTER = 1;

  /** The tokens that continue an expression when they are on the line of its last token. */
  private static final Set<Integer> CALL_OR_INDEX =
      Set.of(TypeScriptParser.OpenParen, TypeScriptParser.OpenBracket);

  private final TypeScriptParserBase parser;
  private final QuickDecisions decisions;

  /**
   * Reads the decisions from the ATN of the parser.
   *
   * @param parser the parser whose decisions are answered
   */
  QuickPrediction(final TypeScriptParserBase parser) {
    this.parser = parser;
    this.decisions = new QuickDecisions(parser.getATN());
  }

  /**
   * Answers a decision when its first tokens settle it.
   *
   * @param decision the decision number
   * @param input the tokens, positioned at the decision
   * @return the alternative, or {@link #UNDECIDED}
   */
  int predict(final int decision, final TokenStream input) {
    if (decision == decisions.loop.decision) {
      return suffix(input);
    }
    if (decision == decisions.statement.decision) {
      return statement(input);
    }
    if (decision == decisions.primary.decision) {
      return primary(input);
    }
    if (decision == decisions.typeArguments.decision) {
      return input.LA(1) == TypeScriptParser.LessThan ? ENTER : UNDECIDED;
    }
    return declaration(decision, input);
  }

  /** Tells whether every decision and alternative this class answers was found in the ATN. */
  boolean complete() {
    return decisions.complete();
  }

  private int declaration(final int decision, final TokenStream input) {
    if (decision == decisions.exportPrefix.decision) {
      return QuickTokens.exportsDeclaration(input) ? ENTER : UNDECIDED;
    }
    if (decision == decisions.classElement.decision) {
      return QuickTokens.startsMethod(input) ? decisions.memberAlternative : UNDECIDED;
    }
    if (decision == decisions.memberKind.decision) {
      return QuickTokens.startsMethod(input) ? decisions.methodAlternative : UNDECIDED;
    }
    return decisions.bodies.contains(decision) && input.LA(1) == TypeScriptParser.OpenBrace
        ? ENTER
        : UNDECIDED;
  }

  private int suffix(final TokenStream input) {
    final int next = input.LA(1);
    final boolean continues =
        next == TypeScriptParser.Dot
            || CALL_OR_INDEX.contains(next) && parser.notLineTerminator();
    if (!continues || next == TypeScriptParser.OpenParen && operandOfNew()) {
      return UNDECIDED;
    }
    for (final QuickDecisions.Suffix suffix : decisions.suffixes) {
      if (suffix.first().contains(next) && parser.precpred(null, suffix.precedence())) {
        return ENTER;
      }
    }
    return UNDECIDED;
  }

  /** In {@code new a.b(c)} the parenthesis belongs to {@code new}, not to a call of the operand. */
  private boolean operandOfNew() {
    return parser.getContext().getParent() instanceof TypeScriptParser.NewExpressionContext;
  }

  private int statement(final TokenStream input) {
    final int keyword = input.LA(1);
    if (keyword == TypeScriptParser.Export) {
      return QuickTokens.exportedDefaultClass(input)
          ? decisions.statementOf(TypeScriptParser.Class)
          : UNDECIDED;
    }
    return QuickTokens.usedAsName(input) || !QuickTokens.startsStatement(keyword, input)
        ? UNDECIDED
        : decisions.statementOf(keyword);
  }

  private int primary(final TokenStream input) {
    final int first = input.LA(1);
    if (first == TypeScriptParser.Async) {
      return QuickTokens.startsAsyncFunction(input) ? decisions.functionAlternative : UNDECIDED;
    }
    final boolean settled =
        first == TypeScriptParser.New
            ? QuickTokens.newOfName(input)
            : QuickTokens.operandFollows(input);
    return settled ? decisions.operatorOf(first) : UNDECIDED;
  }
}
