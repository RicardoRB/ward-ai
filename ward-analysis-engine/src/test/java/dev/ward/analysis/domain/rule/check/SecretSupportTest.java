package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.TextFile;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The small parts of secret matching: entropy, the work budget, positions and text files. */
class SecretSupportTest {

  private static final double FOUR_SYMBOLS = 2.0;

  private static final int LENGTH = 6;
  private static final int SUB_END = 4;
  private static final int SECOND_LINE = 3;
  private static final int THIRD_LINE = 7;

  @Test
  void entropyIsBitsPerCharacterOverTheUtf8Length() {
    assertThat(Entropy.of("")).isZero();
    assertThat(Entropy.of("aaaa")).isZero();
    assertThat(Entropy.of("abcd")).isEqualTo(FOUR_SYMBOLS);
  }

  @Test
  void theWorkBudgetEndsARegionThatReadsTooMuchAndStartsAgainFresh() {
    final WorkBudget budget = new WorkBudget();
    budget.reset(1);

    assertThatThrownBy(
            () -> {
              for (long read = 0; read <= WorkBudget.FLOOR; read++) {
                budget.spend();
              }
            })
        .isInstanceOf(WorkBudget.BudgetExceededException.class);
    budget.reset(1);
    budget.spend();
  }

  @Test
  void aBudgetedViewReadsLikeTheTextAndChargesEveryCharacter() {
    final WorkBudget budget = new WorkBudget();
    final CharSequence view = new BudgetedText("abcdef", budget);

    assertThat(view.length()).isEqualTo(LENGTH);
    assertThat(view.charAt(2)).isEqualTo('c');
    assertThat(view.subSequence(1, SUB_END)).hasToString("bcd");
    assertThat(view.toString()).isEqualTo("abcdef");
  }

  @Test
  void secretsTrimNewlinesAndTakeTheFirstNonEmptyGroup() {
    final Matcher matcher = Pattern.compile("(x)?(\n?k\n?)").matcher("\nk\n");
    assertThat(matcher.find()).isTrue();

    assertThat(SecretText.secret(matcher, 0)).isEqualTo("k");
    assertThat(SecretText.secret(matcher, 2)).isEqualTo("k");
  }

  @Test
  void positionsAreOneBasedLinesAndColumnsAndTheLineIsBoundedByTheRegion() {
    final String text = "ab\ncde\nf";
    final int[] starts = SecretText.lineStarts(text);
    final Matcher matcher = Pattern.compile("de").matcher(text);
    assertThat(matcher.find()).isTrue();

    assertThat(starts).containsExactly(0, SECOND_LINE, THIRD_LINE);
    assertThat(SecretText.span(starts, matcher).startLine()).isEqualTo(2);
    assertThat(SecretText.span(starts, matcher).startColumn()).isEqualTo(2);
    assertThat(SecretText.line(text, new int[] {0, text.length()}, matcher.start()))
        .isEqualTo("cde");
    assertThat(
            SecretText.line(
                text, new int[] {SECOND_LINE + 1, SECOND_LINE + SECOND_LINE}, matcher.start()))
        .isEqualTo("de");
  }

  @Test
  void aTextFileKeepsOffsetsInItsLowerCaseCopyAndRejectsBadInput() {
    final TextFile file = new TextFile("a/B.txt", "AbCİ");

    assertThat(file.lower()).isEqualTo("abcİ");
    assertThat(List.of(file.lower().length())).containsExactly(file.text().length());
    assertThatThrownBy(() -> new TextFile("a\\b", "x"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new TextFile("a", "xx", "x"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
