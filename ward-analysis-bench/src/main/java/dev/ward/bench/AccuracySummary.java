package dev.ward.bench;

import java.util.Locale;

/** The short human-readable table an accuracy run prints, one block per corpus and a total. */
final class AccuracySummary {

  private static final String HEADER = "%-34s %4s %4s %4s  %9s %8s%n";
  private static final String ROW = "%-34s %4d %4d %4d  %9s %8s%n";
  private static final String NONE = "n/a";

  private AccuracySummary() {}

  static String render(final AccuracyResult result) {
    final StringBuilder text = new StringBuilder();
    for (final CorpusAccuracy corpus : result.corpora()) {
      text.append(String.format(Locale.ROOT, "corpus %s (%s)%n", corpus.name(), corpus.commit()));
      text.append(
          String.format(Locale.ROOT, HEADER, "rule", "tp", "fp", "fn", "precision", "recall"));
      for (final RuleScore score : corpus.rules()) {
        text.append(line(score));
      }
      text.append(line(corpus.total()));
    }
    text.append("all corpora\n");
    text.append(
        String.format(Locale.ROOT, HEADER, "rule", "tp", "fp", "fn", "precision", "recall"));
    for (final RuleScore score : result.rules()) {
      text.append(line(score));
    }
    text.append(line(result.total()));
    return text.toString();
  }

  private static String line(final RuleScore score) {
    return String.format(
        Locale.ROOT,
        ROW,
        score.rule(),
        score.truePositives(),
        score.falsePositives(),
        score.falseNegatives(),
        formatted(score.precision()),
        formatted(score.recall()));
  }

  private static String formatted(final Double ratio) {
    return ratio == null ? NONE : String.format(Locale.ROOT, "%.3f", ratio);
  }
}
