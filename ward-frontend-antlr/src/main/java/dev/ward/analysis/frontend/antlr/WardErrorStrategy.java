package dev.ward.analysis.frontend.antlr;

import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.FailedPredicateException;
import org.antlr.v4.runtime.InputMismatchException;
import org.antlr.v4.runtime.NoViableAltException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.IntervalSet;

/**
 * Error strategy that records each parser error once per error episode, as a syntax finding. It
 * runs the standard recovery of {@link DefaultErrorStrategy} unchanged; the parser has no error
 * listener, so ANTLR's six-parameter listener method is not needed.
 */
final class WardErrorStrategy extends DefaultErrorStrategy {

  private final SyntaxFindings findings;

  WardErrorStrategy(final SyntaxFindings findings) {
    super();
    this.findings = findings;
  }

  @Override
  protected void reportNoViableAlternative(final Parser recognizer, final NoViableAltException e) {
    super.reportNoViableAlternative(recognizer, e);
    record(
        e.getOffendingToken(),
        "no viable alternative at input " + getTokenErrorDisplay(e.getOffendingToken()));
  }

  @Override
  protected void reportInputMismatch(final Parser recognizer, final InputMismatchException e) {
    super.reportInputMismatch(recognizer, e);
    record(
        e.getOffendingToken(),
        "mismatched input "
            + getTokenErrorDisplay(e.getOffendingToken())
            + " expecting "
            + e.getExpectedTokens().toString(recognizer.getVocabulary()));
  }

  @Override
  protected void reportFailedPredicate(final Parser recognizer, final FailedPredicateException e) {
    super.reportFailedPredicate(recognizer, e);
    record(e.getOffendingToken(), e.getMessage());
  }

  @Override
  protected void reportUnwantedToken(final Parser recognizer) {
    final boolean fresh = !inErrorRecoveryMode(recognizer);
    super.reportUnwantedToken(recognizer);
    if (fresh) {
      record(
          recognizer.getCurrentToken(),
          "extraneous input "
              + getTokenErrorDisplay(recognizer.getCurrentToken())
              + " expecting "
              + expected(recognizer));
    }
  }

  @Override
  protected void reportMissingToken(final Parser recognizer) {
    final boolean fresh = !inErrorRecoveryMode(recognizer);
    super.reportMissingToken(recognizer);
    if (fresh) {
      record(
          recognizer.getCurrentToken(),
          "missing "
              + expected(recognizer)
              + " at "
              + getTokenErrorDisplay(recognizer.getCurrentToken()));
    }
  }

  private String expected(final Parser recognizer) {
    final IntervalSet tokens = getExpectedTokens(recognizer);
    return tokens.toString(recognizer.getVocabulary());
  }

  private void record(final Token offending, final String message) {
    findings.add(offending.getLine(), offending.getCharPositionInLine() + 1, message);
  }
}
