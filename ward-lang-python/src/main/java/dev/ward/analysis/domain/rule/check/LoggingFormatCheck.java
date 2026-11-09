package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a literal %-format message of a logging call that is truncated or uses a conversion
 * character that the %-format does not support, like pylint {@code logging-format-truncated} and
 * {@code logging-unsupported-format}. An unsupported conversion is reported only when the call
 * passes arguments, as pylint does.
 */
public final class LoggingFormatCheck implements RuleCheck {

  /** The mistakes the check tells apart. */
  public enum Problem {
    /** The format ends inside a conversion specifier. */
    TRUNCATED,
    /** A conversion character outside the supported set. */
    UNSUPPORTED
  }

  private static final Set<String> CALLS = Set.of("atom_expr");
  private static final Pattern CALL =
      Pattern.compile(
          "^(?:\\w+\\.)*(?:logging|log|logger|_logger|LOGGER|LOG|getLogger\\([^()]*\\))"
              + "\\.(?:critical|debug|error|exception|fatal|info|warn|warning)\\(");
  private static final char BACKSLASH = '\\';
  private static final char DOUBLE_QUOTE = '"';
  private static final char SINGLE_QUOTE = '\'';

  private final Problem problem;
  private final String message;

  /**
   * Creates the check.
   *
   * @param problem the mistake to report
   * @param message the message of each finding
   */
  public LoggingFormatCheck(final Problem problem, final String message) {
    this.problem = Objects.requireNonNull(problem, "problem");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, CALLS)) {
      if (violates(SyntaxWalk.text(visit.node()))) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  private boolean violates(final String text) {
    final Matcher call = CALL.matcher(text);
    if (!call.find()) {
      return false;
    }
    final String rest = text.substring(call.end());
    final int close = stringEnd(rest);
    if (close < 0) {
      return false;
    }
    final Outcome outcome = outcome(rest.substring(1, close));
    final int args = positionalArgs(rest.substring(close + 1));
    return switch (problem) {
      case TRUNCATED -> outcome == Outcome.TRUNCATED;
      case UNSUPPORTED -> outcome == Outcome.UNSUPPORTED && args > 0;
    };
  }

  /** The index of the closing quote of the literal that starts the text, or -1. */
  private static int stringEnd(final String rest) {
    if (rest.isEmpty()) {
      return -1;
    }
    final char quote = rest.charAt(0);
    if (quote != DOUBLE_QUOTE && quote != SINGLE_QUOTE) {
      return -1;
    }
    int index = 1;
    while (index < rest.length()) {
      final char c = rest.charAt(index);
      if (c == BACKSLASH) {
        index += 2;
      } else if (c == quote) {
        return index;
      } else {
        index++;
      }
    }
    return -1;
  }

  private static int positionalArgs(final String after) {
    if (!after.startsWith(",")) {
      return 0;
    }
    int count = 0;
    for (final String item : ArgumentList.split(after.substring(1, after.length() - 1))) {
      if (!item.matches("^[A-Za-z_]\\w*=(?!=).*")) {
        count++;
      }
    }
    return count;
  }

  /** How the %-format ends, as pylint's parser reads it. */
  private enum Outcome {
    OK,
    TRUNCATED,
    UNSUPPORTED
  }

  private static Outcome outcome(final String format) {
    try {
      PercentFormatScanner.scan(format);
      return Outcome.OK;
    } catch (PercentFormatScanner.IncompleteFormat ignored) {
      return Outcome.TRUNCATED;
    } catch (PercentFormatScanner.UnsupportedFormat ignored) {
      return Outcome.UNSUPPORTED;
    }
  }
}
