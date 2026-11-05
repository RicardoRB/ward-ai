package dev.ward.bench;

import java.util.List;

/**
 * The result of one accuracy run: each corpus, the sum of each rule over the corpora, and the sum
 * over everything.
 *
 * @param corpora the scored corpora, in the order they were run
 * @param rules the sum of each rule over all corpora, sorted by rule id
 * @param total the sum over all rules and corpora
 */
record AccuracyResult(List<CorpusAccuracy> corpora, List<RuleScore> rules, RuleScore total) {

  /** Copies the lists. */
  AccuracyResult {
    corpora = List.copyOf(corpora);
    rules = List.copyOf(rules);
  }

  /**
   * Sums the corpora.
   *
   * @param corpora the scored corpora
   * @return the result with per-rule and total sums
   */
  static AccuracyResult of(final List<CorpusAccuracy> corpora) {
    final List<RuleScore> all =
        corpora.stream().flatMap(corpus -> corpus.rules().stream()).toList();
    final List<RuleScore> rules =
        all.stream()
            .map(RuleScore::rule)
            .distinct()
            .sorted()
            .map(
                rule ->
                    RuleScore.sum(rule, all.stream().filter(s -> s.rule().equals(rule)).toList()))
            .toList();
    return new AccuracyResult(corpora, rules, RuleScore.sum(RuleScore.TOTAL, all));
  }
}
