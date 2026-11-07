package dev.ward.analysis.domain.rule.duplicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DuplicateCodeOptionsTest {

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
        .isEqualTo(new DuplicateCodeOptions(12, true, true, true));
  }

  @Test
  void keepsTheDefaultsForKeysTheBodyLeavesOut() {
    final DuplicateCodeOptions options = DuplicateCodeOptions.parse("check = \"duplicate-code\"");

    assertThat(options).isEqualTo(new DuplicateCodeOptions(100, false, false, false));
  }

  @Test
  void rejectsAMinimumBelowOne() {
    assertThatThrownBy(() -> DuplicateCodeOptions.parse("min-tokens = 0"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
