package dev.ward.analysis.frontend.antlr.typescript;

import java.util.Set;
import org.antlr.v4.runtime.TokenStream;

/** Answers the decision of the expression loop for {@link QuickPrediction}. */
final class QuickSuffix {
  private static final int ENTER = 1;
  private static final int EXIT = 2;

  /** The tokens that continue an expression when they are on the line of its last token. */
  private static final Set<Integer> CALL_OR_INDEX =
      Set.of(TypeScriptParser.OpenParen, TypeScriptParser.OpenBracket);

  private final TypeScriptParserBase parser;
  private final QuickDecisions decisions;

  QuickSuffix(final TypeScriptParserBase parser, final QuickDecisions decisions) {
    this.parser = parser;
    this.decisions = decisions;
  }

  /**
   * Answers whether the expression goes on with a suffix.
   *
   * @param input the tokens, positioned at the decision
   * @return the alternative, or {@link QuickPrediction#QuickPrediction.UNDECIDED}
   */
  int predict(final TokenStream input) {
    final int next = input.LA(1);
    if (!continues(next)) {
      return QuickPrediction.UNDECIDED;
    }
    if (next == TypeScriptParser.OpenParen && operandOfNew()) {
      return exitNewOperand(input);
    }
    return enters(next) ? ENTER : QuickPrediction.UNDECIDED;
  }

  private boolean continues(final int next) {
    return next == TypeScriptParser.Dot || CALL_OR_INDEX.contains(
        next
    ) && parser.notLineTerminator();
  }

  private int exitNewOperand(final TokenStream input) {
    return input.LA(-1) == TypeScriptParser.Identifier && QuickScans.callEndsOperand(input)
        ? EXIT
        : QuickPrediction.UNDECIDED;
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
    return parser.getContext().getParent() instanceof TypeScriptParser.NewExpressionContext;
  }
}
