package dev.ward.analysis.domain.rule.duplicate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.rule.duplicate.CloneDetector.Clone;
import dev.ward.analysis.domain.rule.duplicate.DuplicateTokens.Token;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class CloneDetectorTest {

  @Test
  void reportsASequenceRepeatedInTwoFilesOnceWithEveryCopy() {
    final Map<String, List<Token>> files =
        Map.of("a.txt", tokens("x y z w p q r s"), "b.txt", tokens("x y z w p q r s"));

    final List<Clone> clones = CloneDetector.detect(new TreeMap<>(files), 4);

    assertThat(clones).hasSize(1);
    assertThat(clones.get(0).length()).isEqualTo(8);
    assertThat(clones.get(0).copies())
        .extracting(CloneDetector.Copy::path)
        .containsExactly("a.txt", "b.txt");
  }

  @Test
  void ignoresARepeatShorterThanTheMinimum() {
    final Map<String, List<Token>> files =
        Map.of("a.txt", tokens("x y z w p q r s"), "b.txt", tokens("x y z w p q r s"));

    assertThat(CloneDetector.detect(new TreeMap<>(files), 9)).isEmpty();
  }

  @Test
  void reportsNothingWhenEveryTokenIsDifferent() {
    final Map<String, List<Token>> files = Map.of("a.txt", tokens("a b c d e f g h"));

    assertThat(CloneDetector.detect(new TreeMap<>(files), 2)).isEmpty();
  }

  /** Tokens named by the words, one per word, on line 1 at the column of the word. */
  private static List<Token> tokens(final String text) {
    final String[] words = text.split(" ");
    final Token[] tokens = new Token[words.length];
    for (int index = 0; index < words.length; index++) {
      final int column = index + 1;
      tokens[index] =
          new Token(words[index], new Span(1, column, 1, column + 1, index, index + 1));
    }
    return List.of(tokens);
  }
}
