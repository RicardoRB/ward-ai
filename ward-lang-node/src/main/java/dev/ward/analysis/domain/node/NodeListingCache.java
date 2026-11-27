package dev.ward.analysis.domain.node;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reads how a function uses its cache object: each mention is {@code if (cache[key])}, {@code
 * return cache[key]}, or {@code cache[key] = { list, ... }} with the list as a shorthand member.
 */
final class NodeListingCache {
  /** The tokens of {@code cache[key]}. */
  static final int INDEXED = 4;

  private static final Set<String> ENDS = Set.of(";", "}", "");
  private static final Set<String> BEFORE = Set.of("{", ",");
  private static final Set<String> AFTER = Set.of(",", "}");

  /** What the writes of the cache put in: the names of the members, and where they are. */
  record Written(List<String> members, List<Integer> places) {}

  private NodeListingCache() {}

  /** The writes of the cache, when every mention of it is one of the three forms and one writes. */
  static Optional<Written> written(final NodeTokens tokens, final String cache, final String key) {
    final List<String> members = new ArrayList<>();
    final List<Integer> places = new ArrayList<>();
    boolean wrote = false;
    for (int index = 0; index < tokens.size(); index++) {
      if (!cache.equals(tokens.text(index)) || ".".equals(tokens.text(index - 1))) {
        continue;
      }
      final int end = index + INDEXED;
      if (tokens.match(index, cache, "[", key, "]").isEmpty()) {
        return Optional.empty();
      }
      if (tokens.match(end, "=", "{").isPresent()) {
        wrote = literal(tokens, end + 1, members, places);
        if (!wrote) {
          return Optional.empty();
        }
      } else if (!read(tokens, index)) {
        return Optional.empty();
      }
    }
    return wrote ? Optional.of(new Written(members, places)) : Optional.empty();
  }

  private static boolean read(final NodeTokens tokens, final int index) {
    final String after = tokens.text(index + INDEXED);
    return tokens.match(index - 2, "if", "(").isPresent() && ")".equals(after)
        || "return".equals(tokens.text(index - 1)) && ENDS.contains(after);
  }

  /** Collects the shorthand members of a literal made only of names, commas and colons. */
  private static boolean literal(
      final NodeTokens tokens,
      final int open,
      final List<String> members,
      final List<Integer> places
  ) {
    final int close = tokens.closing(open);
    for (int index = open + 1; index < close; index++) {
      final boolean name = "Identifier".equals(tokens.kind(index));
      if (!name && !tokens.isPunctuation(index, ",") && !tokens.isPunctuation(index, ":")) {
        return false;
      }
      if (name && BEFORE.contains(tokens.text(index - 1)) && AFTER.contains(
          tokens.text(index + 1)
      )) {
        members.add(tokens.text(index));
        places.add(index);
      }
    }
    return close > open;
  }
}
