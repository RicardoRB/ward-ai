package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reports escape sequences in string literals that ESLint's legacy-escape rules report: an octal
 * escape such as {@code \1} (at the string's opening quote, as ESLint {@code no-octal-escape}
 * does), or a decimal escape {@code \8} or {@code \9} (at the backslash, as ESLint {@code
 * no-nonoctal-decimal-escape} does). The source is read line by line; strings are single-line.
 */
public final class StringEscapeCheck implements RuleCheck {

  private static final String QUOTES = "'\"`";
  private static final char BACKSLASH = '\\';

  private final boolean decimal;
  private final String message;

  /**
   * Creates the check.
   *
   * @param decimal whether the decimal escapes {@code \8} and {@code \9} are reported, instead of
   *     the octal escapes
   * @param message the message of each finding
   */
  public StringEscapeCheck(final boolean decimal, final String message) {
    this.decimal = decimal;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    final List<RuleMatch> matches = new ArrayList<>();
    int lineStart = 0;
    int line = 1;
    for (final String text : unit.text().split("\n", -1)) {
      scan(text, line, lineStart, matches);
      lineStart += text.length() + 1;
      line++;
    }
    return List.copyOf(matches);
  }

  /** Scans one line for the strings on it, and reports the escapes inside each. */
  private void scan(
      final String text, final int line, final int lineStart, final List<RuleMatch> matches) {
    int index = 0;
    while (index < text.length()) {
      final int open = nextQuote(text, index);
      if (open < 0) {
        return;
      }
      index = stringEnd(text, open, line, lineStart, matches);
    }
  }

  private static int nextQuote(final String text, final int from) {
    for (int index = from; index < text.length(); index++) {
      if (QUOTES.indexOf(text.charAt(index)) >= 0) {
        return index;
      }
    }
    return -1;
  }

  /** Reports the escapes of the string opened at {@code open}; returns the index after it. */
  private int stringEnd(
      final String text,
      final int open,
      final int line,
      final int lineStart,
      final List<RuleMatch> matches) {
    final char quote = text.charAt(open);
    int index = open + 1;
    while (index < text.length()) {
      final char c = text.charAt(index);
      if (c == BACKSLASH && index + 1 < text.length()) {
        final int column = reportColumn(text, index, open);
        if (column >= 0) {
          matches.add(at(line, lineStart, column));
        }
        index += 2;
      } else if (c == quote) {
        return index + 1;
      } else {
        index++;
      }
    }
    return text.length();
  }

  /** The column to report for the escape at {@code backslash}, or -1 when it is not reported. */
  private int reportColumn(final String text, final int backslash, final int stringStart) {
    if (decimal) {
      final char next = text.charAt(backslash + 1);
      return next == '8' || next == '9' ? backslash : -1;
    }
    return isOctal(text, backslash + 1) ? stringStart : -1;
  }

  /** An octal escape starts with 1 to 7, or with 0 followed by an octal digit. */
  private static boolean isOctal(final String text, final int digit) {
    final char first = text.charAt(digit);
    return (first >= '1' && first <= '7')
        || (first == '0'
            && digit + 1 < text.length()
            && text.charAt(digit + 1) >= '0'
            && text.charAt(digit + 1) <= '7');
  }

  private RuleMatch at(final int line, final int lineStart, final int column) {
    final int offset = lineStart + column;
    return new RuleMatch(new Span(line, column + 1, line, column + 2, offset, offset + 1), message);
  }
}
