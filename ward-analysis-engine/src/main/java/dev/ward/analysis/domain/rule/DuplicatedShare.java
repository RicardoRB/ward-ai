package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.rule.CloneDetector.Clone;
import dev.ward.analysis.domain.rule.CloneDetector.Copy;
import dev.ward.analysis.domain.rule.DuplicateTokens.Token;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

/**
 * How much of the compared code is covered by copies. A token counts once, however many clones
 * contain it, so the share is the tokens inside at least one copy over all tokens of the files.
 */
public final class DuplicatedShare {

  private static final long PERCENT = 100L;

  private final long duplicated;
  private final long total;

  private DuplicatedShare(final long duplicated, final long total) {
    this.duplicated = duplicated;
    this.total = total;
  }

  /** Measures {@code clones}, found among {@code files}, against the tokens of those files. */
  public static DuplicatedShare of(
      final SortedMap<String, List<Token>> files, final List<Clone> clones) {
    long duplicated = 0;
    long total = 0;
    for (final Map.Entry<String, List<Token>> file : files.entrySet()) {
      final List<Copy> copies = new ArrayList<>();
      for (final Clone clone : clones) {
        clone.copies().stream()
            .filter(copy -> copy.path().equals(file.getKey()))
            .forEach(copies::add);
      }
      total += file.getValue().size();
      duplicated += file.getValue().stream().filter(token -> covered(token, copies)).count();
    }
    return new DuplicatedShare(duplicated, total);
  }

  private static boolean covered(final Token token, final List<Copy> copies) {
    return copies.stream()
        .anyMatch(
            copy ->
                token.span().startOffset() >= copy.span().startOffset()
                    && token.span().startOffset() < copy.span().endOffset());
  }

  /** The tokens inside at least one copy. */
  public long duplicatedTokens() {
    return duplicated;
  }

  /** The tokens of all compared files. */
  public long totalTokens() {
    return total;
  }

  /** The share as a percentage, rounded down; zero when there are no tokens. */
  public long percent() {
    return total == 0 ? 0 : duplicated * PERCENT / total;
  }

  /** Whether the share is strictly above {@code maxPercent}, compared without rounding. */
  public boolean exceeds(final int maxPercent) {
    return duplicated * PERCENT > maxPercent * total;
  }
}
