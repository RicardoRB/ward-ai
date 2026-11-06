package dev.ward.analysis.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuleOptionTest {

  private static final long FIVE = 5L;

  @Test
  void optionsCheckTheirValues() {
    assertThat(RuleOption.integer("max", 1).problem(0L))
        .contains("expected an integer >= 1, got 0");
    assertThat(RuleOption.integer("max", 1).problem("sixty"))
        .contains("expected an integer >= 1, got \"sixty\"");
    assertThat(RuleOption.integer("max", 1).problem(FIVE)).isEmpty();
    assertThat(new RuleOption("flag", RuleOptionType.BOOLEAN).problem("yes")).isPresent();
    assertThat(new RuleOption("text", RuleOptionType.STRING).problem(1L)).isPresent();
    assertThat(new RuleOption("p", RuleOptionType.REGEX).problem("[")).isPresent();
    assertThat(new RuleOption("p", RuleOptionType.REGEX).problem("[a-z]+")).isEmpty();
    assertThat(new RuleOption("l", RuleOptionType.STRING_LIST).problem(List.of(1L))).isPresent();
    assertThat(new RuleOption("l", RuleOptionType.STRING_LIST).problem(List.of("a"))).isEmpty();
    final RuleOption mode =
        new RuleOption(
            "mode", RuleOptionType.ENUM, null, Long.MIN_VALUE, Long.MAX_VALUE, List.of("a", "b"));
    assertThat(mode.problem("c")).contains("expected one of [a, b], got \"c\"");
    assertThat(mode.problem("a")).isEmpty();
  }

  @Test
  void declarationsAreValidated() {
    assertThatThrownBy(() -> new RuleOption("Bad", RuleOptionType.STRING))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RuleOption("m", RuleOptionType.ENUM))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void strictSelectsWhatStandardSelects() {
    final Set<RulePreset> standard = Set.of(RulePreset.STANDARD);

    assertThat(RulePreset.STANDARD.selects(standard)).isTrue();
    assertThat(RulePreset.STRICT.selects(standard)).isTrue();
    assertThat(RulePreset.STANDARD.selects(Set.of(RulePreset.STRICT))).isFalse();
    assertThat(RulePreset.NONE.selects(standard)).isFalse();
  }
}
