package dev.ward.analysis.domain.node;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Finds, in a body, the blocks that run only for an entry of a directory whose name starts with a
 * key: {@code if (entry.startsWith(`${key}...`)) { ... }} inside {@code for (const entry of
 * entries)}, where {@code const entries = readdirSync(...)} is the only mention of the listing
 * besides the loop.
 */
final class NodeListingLoop {
  private static final String[] LOOP = {"for", "(", "const", "*", "of", "*", ")", "{"};
  private static final String[] TAIL = {"`", ")", ")", "{"};
  private static final String[] HEAD = {"if", "(", "", ".", "startsWith", "(", "`", "${", "", "}"};
  private static final int ENTRY_AT = 2;
  private static final int KEY_AT = 8;
  private static final String ATOM = "TemplateStringAtom";
  private static final int ENTRY = 0;
  private static final int LISTING = 1;

  /** The tokens of a block of the body, from the {@code {} to the {@code }}. */
  record Range(int from, int to) {
    boolean holds(final int index) {
      return index > from && index < to;
    }
  }

  private NodeListingLoop() {}

  /** The blocks that run only for a name that starts with the key, when there is one. */
  static Optional<List<Range>> frames(final NodeTokens tokens, final String key) {
    for (int index = 0; index < tokens.size(); index++) {
      final Optional<List<String>> loop = tokens.match(index, LOOP);
      if (loop.isPresent() && listed(tokens, loop.get().get(LISTING))) {
        final int open = index + LOOP.length - 1;
        final List<Range> frames = frames(tokens, loop.get().get(ENTRY), key, open);
        if (!frames.isEmpty()) {
          return Optional.of(frames);
        }
      }
    }
    return Optional.empty();
  }

  private static List<Range> frames(
      final NodeTokens tokens,
      final String entry,
      final String key,
      final int open
  ) {
    final List<Range> frames = new ArrayList<>();
    final int close = tokens.closing(open);
    for (int index = open; index < close; index++) {
      frame(tokens, index, entry, key).ifPresent(frames::add);
    }
    return frames;
  }

  /** The block of {@code if (entry.startsWith(`${key}text`))} that starts at the token. */
  private static Optional<Range> frame(
      final NodeTokens tokens,
      final int index,
      final String entry,
      final String key
  ) {
    final String[] head = HEAD.clone();
    head[ENTRY_AT] = entry;
    head[KEY_AT] = key;
    if (tokens.match(index, head).isEmpty()) {
      return Optional.empty();
    }
    int next = index + HEAD.length;
    while (ATOM.equals(tokens.kind(next))) {
      next++;
    }
    final int open = next + TAIL.length - 1;
    return tokens.match(next, TAIL).isPresent()
        ? Optional.of(new Range(open, tokens.closing(open)))
        : Optional.empty();
  }

  /** Whether the name is a listing of a directory that nothing else touches. */
  private static boolean listed(final NodeTokens tokens, final String listing) {
    int mentions = 0;
    boolean declared = false;
    for (int index = 0; index < tokens.size(); index++) {
      if (listing.equals(tokens.text(index)) && !".".equals(tokens.text(index - 1))) {
        mentions++;
        declared |=
            tokens.match(index - 1, "const", listing, "=", "readdirSync", "(").isPresent()
                || tokens.match(index - 1, "const", listing, "=", "*", ".", "readdirSync", "(")
                    .isPresent();
      }
    }
    return declared && mentions == 2;
  }
}
