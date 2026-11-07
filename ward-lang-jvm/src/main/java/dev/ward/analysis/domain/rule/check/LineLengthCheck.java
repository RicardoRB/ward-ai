package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Fails each line of the file that is longer than the limit. A tab advances to the next multiple of
 * the tab width, and a line that matches the ignore pattern is not measured.
 */
public final class LineLengthCheck implements UnitCheck {

  private static final int MIN_TAB_WIDTH = 1;

  private final int max;
  private final int tabWidth;
  private final Pattern ignore;
  private final String message;

  /**
   * Creates the check.
   *
   * @param max the largest allowed line length
   * @param tabWidth the width a tab expands to, at least one
   * @param ignorePattern a regular expression; a line that it finds is not measured
   * @param message the message; {@code {value}} and {@code {max}} are filled in
   * @throws IllegalArgumentException if {@code max} is negative or {@code tabWidth} is not positive
   */
  public LineLengthCheck(
      final int max, final int tabWidth, final String ignorePattern, final String message) {
    this.max = CheckSupport.nonNegative(max, "max");
    if (tabWidth < MIN_TAB_WIDTH) {
      throw new IllegalArgumentException("tab-width must be positive");
    }
    this.tabWidth = tabWidth;
    this.ignore = Pattern.compile(Objects.requireNonNull(ignorePattern, "ignorePattern"));
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    final List<String> lines = file.lines();
    int offset = 0;
    for (int index = 0; index < lines.size(); index++) {
      final String line = lines.get(index);
      final int length = expandedLength(line);
      if (length > max && !ignore.matcher(line).find()) {
        matches.add(report(index + 1, line, offset, length));
      }
      offset += line.length() + 1;
    }
    return List.copyOf(matches);
  }

  private RuleMatch report(
      final int number, final String line, final int offset, final int length) {
    final Span span =
        new Span(number, 1, number, line.length() + 1, offset, offset + line.length());
    return CheckSupport.at(
        span, CheckSupport.fill(message, String.valueOf(length), String.valueOf(max)));
  }

  private int expandedLength(final String line) {
    int column = 0;
    for (int index = 0; index < line.length(); index++) {
      column = line.charAt(index) == '\t' ? column + tabWidth - column % tabWidth : column + 1;
    }
    return column;
  }
}
