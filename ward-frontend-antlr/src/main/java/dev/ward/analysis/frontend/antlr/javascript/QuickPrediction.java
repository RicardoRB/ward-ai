package dev.ward.analysis.frontend.antlr.javascript;

import java.util.Set;
import org.antlr.v4.runtime.TokenStream;

/**
 * Decides four decisions of the JavaScript grammar from one or two tokens, where the adaptive
 * prediction of ANTLR would read to the end of the statement. Statements end without a semicolon,
 * so the grammar lets {@code f} and {@code (x)} be two statements, and every keyword is also a
 * name: prediction then follows both readings for hundreds of tokens and finds them ambiguous, and
 * the first alternative wins. This class returns that first alternative at once, in the cases
 * where no valid program has the other reading:
 *
 * <ul>
 *   <li>after an expression, a {@code .}, and a {@code (} or {@code [} on the same line, continue
 *       the expression when the precedence allows it; the {@code (} after the path of the operand
 *       of {@code new} belongs to {@code new};
 *   <li>a statement that starts with a reserved statement keyword is that statement;
 *   <li>{@code await}, {@code typeof}, {@code void} and {@code delete} before the start of an
 *       operand are operators;
 *   <li>{@code async} before {@code function}, before {@code x =>} or before a parenthesis that
 *       closes in front of {@code =>} starts a function, and {@code new Name(} and {@code new
 *       a.b(} are the forms of {@code new} with arguments;
 *   <li>an {@code else} after the statement of an {@code if} belongs to it.
 * </ul>
 *
 * <p>{@link QuickDecisions} reads the decision numbers and alternatives from the ATN, and {@link
 * QuickTokens} reads the tokens; {@code QuickPredictionTest} checks that every decision is found.
 */
final class QuickPrediction {
  /** No quick answer: the adaptive prediction decides. */
  static final int UNDECIDED = AtnPaths.NONE;

  private static final int ENTER = 1;
  private static final int EXIT = 2;

  /** The tokens that continue an expression when they are on the line of its last token. */
  private static final Set<Integer> CALL_OR_INDEX =
      Set.of(JavaScriptParser.OpenParen, JavaScriptParser.OpenBracket);

  private final JavaScriptParserBase parser;
  private final QuickDecisions decisions;

  /**
   * Reads the decisions from the ATN of the parser.
   *
   * @param parser the parser whose decisions are answered
   */
  QuickPrediction(final JavaScriptParserBase parser) {
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
    if (decision == decisions.ifElse.decision) {
      return input.LA(1) == JavaScriptParser.Else && !QuickTokens.usedAsName(input)
          ? ENTER
          : UNDECIDED;
    }
    return UNDECIDED;
  }

  /** Tells whether every decision and alternative this class answers was found in the ATN. */
  boolean complete() {
    return decisions.complete();
  }

  private int suffix(final TokenStream input) {
    final int next = input.LA(1);
    if (!continues(next)) {
      return UNDECIDED;
    }
    if (next == JavaScriptParser.OpenParen && operandOfNew()) {
      return exitNewOperand(input);
    }
    return enters(next) ? ENTER : UNDECIDED;
  }

  private boolean continues(final int next) {
    return next == JavaScriptParser.Dot || CALL_OR_INDEX.contains(
        next
    ) && parser.notLineTerminator();
  }

  private int exitNewOperand(final TokenStream input) {
    return input.LA(-1) == JavaScriptParser.Identifier && QuickTokens.callEndsOperand(input)
        ? EXIT
        : UNDECIDED;
  }

  private boolean enters(final int next) {
    for (final QuickDecisions.Suffix suffix : decisions.suffixes) {
      if (suffix.first().contains(next) && parser.precpred(null, suffix.precedence())) {
        return true;
      }
    }
    return false;
  }

  /** In {@code new a.b(c)} the parenthesis belongs to {@code new}, not to a call of the operand. */
  private boolean operandOfNew() {
    return parser.getContext().getParent() instanceof JavaScriptParser.NewExpressionContext;
  }

  private int statement(final TokenStream input) {
    final int keyword = input.LA(1);
    return QuickTokens.usedAsName(input) || !QuickTokens.startsStatement(keyword, input)
        ? UNDECIDED
        : decisions.statementOf(keyword);
  }

  private int primary(final TokenStream input) {
    final int first = input.LA(1);
    if (first == JavaScriptParser.Async) {
      return QuickTokens.startsAsyncFunction(input) ? decisions.functionAlternative : UNDECIDED;
    }
    if (first == JavaScriptParser.New) {
      return newForm(input);
    }
    return QuickTokens.operandFollows(input) ? decisions.operatorOf(first) : UNDECIDED;
  }

  /** In {@code new a.b(c)} the operand is a path of names, and the arguments belong to new. */
  private int newForm(final TokenStream input) {
    if (QuickTokens.newOfName(input)) {
      return decisions.newAlternatives.get(0);
    }
    return QuickTokens.newOfPath(input) ? decisions.newAlternatives.get(1) : UNDECIDED;
  }
}
