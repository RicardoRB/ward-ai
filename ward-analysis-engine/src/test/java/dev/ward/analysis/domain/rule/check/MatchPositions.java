package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;

/** The positions of matches, and the matches of a check on a source read as a file. */
final class MatchPositions {

  static final int LINE_3 = 3;
  static final int LINE_4 = 4;
  static final int LINE_5 = 5;
  static final int LINE_6 = 6;
  static final int LINE_7 = 7;
  private static final String COLON = ":";
  private static final String FIRST_COLUMN = ":1";

  private MatchPositions() {}

  /** Returns the positions of {@code matches} as {@code line:column} texts, in order. */
  static List<String> at(final List<RuleMatch> matches) {
    return matches.stream()
        .map(match -> match.span().startLine() + COLON + match.span().startColumn())
        .toList();
  }

  /** Returns the position of the first column of {@code line}, as {@code line:column} text. */
  static String col1(final int line) {
    return line + FIRST_COLUMN;
  }

  /** Returns the matches of {@code check} on {@code source}, read as the file {@code path}. */
  static List<RuleMatch> textMatches(
      final RuleCheck check, final String path, final String source) {
    return check.matches(
        ImportTrees.unit(source), null, new SourceUnit(path, AnalysisLanguage.JAVA, source));
  }
}
