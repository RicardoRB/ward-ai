package dev.ward.analysis.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DuplicateCodeOptionsTest {

  private static final int CUSTOM_MIN_TOKENS = 12;
  private static final int CUSTOM_SHARE = 5;

  @Test
  void readsEveryOptionFromTheBody() {
    final DuplicateCodeOptions options =
        DuplicateCodeOptions.parse(
            """
            check = "duplicate-code"
            min-tokens = 12
            ignore-literals = true
            ignore-identifiers = true
            ignore-annotations = true
            """);

    assertThat(options)
        .isEqualTo(
            new DuplicateCodeOptions(
                CUSTOM_MIN_TOKENS, true, true, true, DuplicateCodeOptions.NO_SHARE_LIMIT));
  }

  @Test
  void keepsTheDefaultsForKeysTheBodyLeavesOut() {
    final DuplicateCodeOptions options = DuplicateCodeOptions.parse("check = \"duplicate-code\"");

    assertThat(options)
        .isEqualTo(
            new DuplicateCodeOptions(
                DuplicateCodeOptions.DEFAULT_MIN_TOKENS,
                false,
                false,
                false,
                DuplicateCodeOptions.NO_SHARE_LIMIT));
  }

  @Test
  void rejectsAMinimumBelowOne() {
    assertThatThrownBy(() -> DuplicateCodeOptions.parse("min-tokens = 0"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void readsAndValidatesTheShareLimit() {
    assertThat(DuplicateCodeOptions.parse("max-duplicated-percent = 5").maxDuplicatedPercent())
        .isEqualTo(CUSTOM_SHARE);
    assertThatThrownBy(() -> DuplicateCodeOptions.parse("max-duplicated-percent = 101"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
