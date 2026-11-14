package dev.ward.analysis.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.rule.DuplicateTokens.Token;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class DuplicatedShareTest {

  private static final int TOKENS = 10;
  private static final int COPY_LENGTH = 3;
  private static final int DUPLICATED = 7;
  private static final int PERCENT = 70;
  private static final int BELOW = 69;

  private static List<Token> tokens() {
    final List<Token> list = new ArrayList<>();
    for (int offset = 0; offset < TOKENS; offset++) {
      list.add(new Token("t" + offset, new Span(1, offset + 1, 1, offset + 2, offset, offset + 1)));
    }
    return list;
  }

  private static CloneDetector.Copy copy(final int from) {
    return new CloneDetector.Copy(
        "A", new Span(1, from + 1, 1, from + COPY_LENGTH + 1, from, from + COPY_LENGTH));
  }

  @Test
  void countsEachTokenOnceAndComparesWithoutRounding() {
    final SortedMap<String, List<Token>> files = new TreeMap<>();
    files.put("A", tokens());
    final CloneDetector.Clone first =
        new CloneDetector.Clone(COPY_LENGTH, List.of(copy(0), copy(5)));
    final CloneDetector.Clone overlapping = new CloneDetector.Clone(COPY_LENGTH, List.of(copy(1)));

    final DuplicatedShare share = DuplicatedShare.of(files, List.of(first, overlapping));

    assertThat(share.duplicatedTokens()).isEqualTo(DUPLICATED);
    assertThat(share.totalTokens()).isEqualTo(TOKENS);
    assertThat(share.percent()).isEqualTo(PERCENT);
    assertThat(share.exceeds(BELOW)).isTrue();
    assertThat(share.exceeds(PERCENT)).isFalse();
  }

  @Test
  void anEmptyProjectDuplicatesNothing() {
    final DuplicatedShare share = DuplicatedShare.of(new TreeMap<>(), List.of());

    assertThat(share.percent()).isZero();
    assertThat(share.exceeds(0)).isFalse();
  }
}
