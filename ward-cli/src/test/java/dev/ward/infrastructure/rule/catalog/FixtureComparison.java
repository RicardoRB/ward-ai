package dev.ward.infrastructure.rule.catalog;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Compares the positions a rule reported with the positions a fixture expects, and says what
 * differs in terms of the rule, the language and the line.
 */
final class FixtureComparison {

  private FixtureComparison() {}

  /**
   * Returns one message per difference between the expected positions and the findings.
   *
   * @param ruleId the id of the rule that ran
   * @param language the language of the fixture
   * @param file the fixture file that ran, for the message
   * @param expected the expected {@code line:column} texts, in order; empty for a passing fixture
   * @param findings the findings of the rule on the file
   */
  static List<String> problems(
      final String ruleId,
      final AnalysisLanguage language,
      final String file,
      final List<String> expected,
      final List<AnalysisFinding> findings) {
    final List<String> reported = findings.stream().map(FixtureComparison::position).toList();
    final List<String> problems = new ArrayList<>();
    final String subject = ruleId + " (" + language.name().toLowerCase(Locale.ROOT) + ")";
    for (final String position : expected) {
      if (!reported.contains(position)) {
        problems.add(
            subject
                + ": "
                + file
                + " expects a finding at "
                + describe(position)
                + " but the rule did not report it");
      }
    }
    for (final String position : reported) {
      if (!expected.contains(position)) {
        problems.add(
            subject + ": " + file + " reports an unexpected finding at " + describe(position));
      }
    }
    if (problems.isEmpty() && !reported.equals(expected)) {
      problems.add(subject + ": " + file + " reports " + reported + ", expected " + expected);
    }
    return problems;
  }

  private static String describe(final String position) {
    final String[] parts = position.split(":");
    return "line " + parts[0] + ", column " + parts[1];
  }

  private static String position(final AnalysisFinding finding) {
    return finding.line() + ":" + finding.column();
  }
}
