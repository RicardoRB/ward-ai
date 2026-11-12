package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.TextFile;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Where a secret rule matches: lines, windows around keyword hits, and sliding windows. */
class SecretRegionsTest {

  private static final int LONG = SecretRegions.LONG_LINE;

  private static final int LINES = 3;
  private static final int FIRST_LINE_END = 3;
  private static final int LAST_LINE_START = 6;
  private static final int LAST_LINE_END = 8;
  private static final int GAP = 10_000;
  private static final int BEFORE_KEYWORD = 2_000;
  private static final int LEAD = 512;
  private static final int BIG = 40_000;
  private static final int TEXT_WINDOW = 16 * 1024;

  private static SecretRule rule(final List<String> keywords, final boolean multiline) {
    return new SecretRule(
        Optional.of(Pattern.compile("x")),
        Optional.empty(),
        keywords,
        Optional.empty(),
        0,
        multiline,
        List.of(),
        "m");
  }

  private static List<int[]> regions(final String text, final SecretRule rule) {
    return SecretRegions.of(new TextFile("a", text), rule);
  }

  @Test
  void shortLinesAreMatchedOneByOneWithTheirTerminator() {
    final List<int[]> regions = regions("ab\ncd\nef", rule(List.of(), false));

    assertThat(regions).hasSize(LINES);
    assertThat(regions.get(0)).containsExactly(0, FIRST_LINE_END);
    assertThat(regions.get(2)).containsExactly(LAST_LINE_START, LAST_LINE_END);
  }

  @Test
  void aLongLineWithoutKeywordsSlidesInOverlappingWindows() {
    final List<int[]> regions = regions("y".repeat(LINES * LONG), rule(List.of(), false));

    assertThat(regions).hasSizeGreaterThan(LINES);
    assertThat(regions.get(0)).containsExactly(0, LONG);
    assertThat(regions.get(1)[0]).isLessThan(LONG);
    assertThat(regions.get(regions.size() - 1)[1]).isEqualTo(LINES * LONG);
  }

  @Test
  void aLongLineIsMatchedOnlyAroundItsKeywordHitsAndDenseHitsStaySmall() {
    final String sparse = "y".repeat(GAP) + "key" + "y".repeat(GAP);
    final String dense = "key ".repeat(GAP);
    final SecretRule keyed = rule(List.of("key"), false);

    final List<int[]> around = regions(sparse, keyed);
    final List<int[]> many = regions(dense, keyed);

    assertThat(around).hasSize(1);
    assertThat(around.get(0)[0]).isLessThanOrEqualTo(GAP).isGreaterThan(GAP - LONG);
    assertThat(many.stream().mapToLong(region -> region[1] - region[0]).sum())
        .isLessThan(dense.length() * 2L);
  }

  @Test
  void aMultiLineRuleMatchesInWindowsThatStartBeforeTheKeyword() {
    final String text = "z".repeat(BEFORE_KEYWORD) + "begin" + "z".repeat(BIG);

    final List<int[]> regions = regions(text, rule(List.of("begin"), true));

    assertThat(regions.get(0)[0]).isEqualTo(BEFORE_KEYWORD - LEAD);
    assertThat(regions.get(0)[1] - regions.get(0)[0]).isEqualTo(TEXT_WINDOW);
  }

  @Test
  void aMultiLineRuleWithoutKeywordsSlidesAndWithNoHitHasNoRegion() {
    final String text = "z".repeat(BIG);

    assertThat(regions(text, rule(List.of(), true)).size()).isGreaterThan(2);
    assertThat(regions(text, rule(List.of("begin"), true))).isEmpty();
  }
}
