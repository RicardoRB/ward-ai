package dev.ward.application.rules;

import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Names the message of an unknown rule id, with the closest known id when one is near. */
final class RuleIdSuggestion {
  private static final int MAX_DISTANCE = 3;

  private RuleIdSuggestion() {}

  /** Returns the message for {@code id}, which is not in {@code catalog}. */
  static String unknown(final String id, final List<RuleDefinition> catalog) {
    final Optional<String> closest =
        catalog.stream()
            .map(RuleDefinition::id)
            .min(Comparator.comparingInt(candidate -> distance(id, candidate)))
            .filter(candidate -> distance(id, candidate) <= MAX_DISTANCE);
    return "unknown rule id \""
        + id
        + "\""
        + closest.map(candidate -> "; did you mean \"" + candidate + "\"?").orElse("");
  }

  /** The Levenshtein distance between two ids, with two rows reused across the loop. */
  private static int distance(final String left, final String right) {
    int[] previous = new int[right.length() + 1];
    int[] current = new int[right.length() + 1];
    for (int j = 0; j <= right.length(); j++) {
      previous[j] = j;
    }
    for (int i = 1; i <= left.length(); i++) {
      current[0] = i;
      for (int j = 1; j <= right.length(); j++) {
        final int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
        current[j] =
            Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
      }
      final int[] swap = previous;
      previous = current;
      current = swap;
    }
    return previous[right.length()];
  }
}
