package dev.ward.bench;

import java.util.List;

/**
 * The accuracy of every measured rule on one corpus.
 *
 * @param name the corpus directory name or the external corpus name
 * @param commit the pinned commit of an external corpus, or {@code checked-in}
 * @param rules one score per measured rule, sorted by rule id
 */
record CorpusAccuracy(String name, String commit, List<RuleScore> rules) {

  /** The commit of a corpus that is checked into this repository. */
  static final String CHECKED_IN = "checked-in";

  /** Copies the list. */
  CorpusAccuracy {
    rules = List.copyOf(rules);
  }

  /** Returns the sum over the measured rules. */
  RuleScore total() {
    return RuleScore.sum(RuleScore.TOTAL, rules);
  }
}
