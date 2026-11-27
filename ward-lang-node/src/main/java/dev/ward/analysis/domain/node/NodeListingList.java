package dev.ward.analysis.domain.node;

import java.util.List;
import java.util.Set;

/**
 * Checks that a local array grows only inside the blocks that run for a name with the key as its
 * start: it is declared once as {@code const list = []}, its only other mentions are {@code
 * list.push(...)} inside such a block and the shorthand members of the cache write.
 */
final class NodeListingList {
  private NodeListingList() {}

  /** Whether every mention of the list is its declaration, a push in a block, or a cache write. */
  static boolean filled(
      final NodeTokens tokens,
      final String list,
      final List<NodeListingLoop.Range> frames,
      final List<Integer> writes
  ) {
    int declarations = 0;
    for (int index = 0; index < tokens.size(); index++) {
      if (!list.equals(tokens.text(index)) || ".".equals(tokens.text(index - 1))) {
        continue;
      }
      if (declaration(tokens, index)) {
        declarations++;
      } else if (!push(tokens, index, frames) && !writes.contains(index)) {
        return false;
      }
    }
    return declarations == 1;
  }

  private static boolean push(
      final NodeTokens tokens,
      final int index,
      final List<NodeListingLoop.Range> frames
  ) {
    return tokens.match(index + 1, ".", "push", "(").isPresent()
        && frames.stream().anyMatch(frame -> frame.holds(index));
  }

  /** {@code const list = []}, with an optional type between the name and the equals sign. */
  private static boolean declaration(final NodeTokens tokens, final int index) {
    if (!Set.of("const", "let").contains(tokens.text(index - 1))) {
      return false;
    }
    int next = index + 1;
    while (next < tokens.size() && !tokens.isPunctuation(next, "=") && !tokens.isPunctuation(
        next,
        ";"
    )) {
      next++;
    }
    return tokens.match(next, "=", "[", "]").isPresent();
  }
}
