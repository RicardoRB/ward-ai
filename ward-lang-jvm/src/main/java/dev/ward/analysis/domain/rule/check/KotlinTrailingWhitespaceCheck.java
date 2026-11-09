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
 * Reports each line of a Kotlin file that ends in spaces or tabs, as detekt's {@code
 * TrailingWhitespace} does. The finding covers the trailing blanks of the line. A carriage return
 * before the line feed is not blank.
 */
public final class KotlinTrailingWhitespaceCheck implements RuleCheck {

  private static final char LINE_FEED = '\n';
  private static final char CARRIAGE_RETURN = '\r';
  private static final char SPACE = ' ';
  private static final char TAB = '\t';

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public KotlinTrailingWhitespaceCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Always empty: the check needs the file text, which only the unit overload has. */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    final String text = unit.text();
    final List<RuleMatch> found = new ArrayList<>();
    int lineStart = 0;
    int line = 1;
    while (lineStart <= text.length()) {
      final int lineEnd = lineEnd(text, lineStart);
      final int contentEnd = contentEnd(text, lineStart, lineEnd);
      final int blankStart = blankStart(text, lineStart, contentEnd);
      if (blankStart < contentEnd) {
        found.add(blankRun(line, lineStart, blankStart, contentEnd));
      }
      if (lineEnd == text.length()) {
        break;
      }
      lineStart = lineEnd + 1;
      line++;
    }
    return List.copyOf(found);
  }

  private RuleMatch blankRun(final int line, final int lineStart, final int from, final int to) {
    final int column = from - lineStart + 1;
    final Span span = new Span(line, column, line, column + to - from, from, to);
    return new RuleMatch(span, message);
  }

  private static int lineEnd(final String text, final int lineStart) {
    final int end = text.indexOf(LINE_FEED, lineStart);
    return end < 0 ? text.length() : end;
  }

  private static int contentEnd(final String text, final int lineStart, final int lineEnd) {
    final boolean carriageReturn =
        lineEnd > lineStart && text.charAt(lineEnd - 1) == CARRIAGE_RETURN;
    return carriageReturn ? lineEnd - 1 : lineEnd;
  }

  private static int blankStart(final String text, final int lineStart, final int contentEnd) {
    int start = contentEnd;
    while (start > lineStart && isBlank(text.charAt(start - 1))) {
      start--;
    }
    return start;
  }

  private static boolean isBlank(final char ch) {
    return ch == SPACE || ch == TAB;
  }
}
