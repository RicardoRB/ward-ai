package dev.ward.analysis.application.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.rule.RuleConfigurationException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.tomlj.TomlParseResult;

class TomlValuesTest {

  private static final int COUNT = 3;
  private static final int FALLBACK = 7;
  private static final String BODY =
      """
      name = "x"
      count = 3
      big = 99999999999
      negative = -1
      list = ["a", "b"]
      numbers = [1]
      [table]
      a = "b"
      """;

  private static TomlParseResult toml() {
    return TomlValues.parse(BODY);
  }

  @Test
  void readsTypedValues() {
    assertThat(TomlValues.required(toml(), "name")).isEqualTo("x");
    assertThat(TomlValues.integer(toml(), "count")).isEqualTo(COUNT);
    assertThat(TomlValues.optionalInteger(toml(), "count", FALLBACK)).isEqualTo(COUNT);
    assertThat(TomlValues.optionalInteger(toml(), "absent", FALLBACK)).isEqualTo(FALLBACK);
    assertThat(TomlValues.strings(toml(), "list")).containsExactlyInAnyOrder("a", "b");
    assertThat(TomlValues.optionalStrings(toml(), "absent")).isEmpty();
    assertThat(TomlValues.kindMap(toml(), "table")).isEqualTo(Map.of("a", "b"));
    assertThat(TomlValues.kindMap(toml(), "absent")).isEmpty();
    assertThat(TomlLists.optional(toml(), "list")).containsExactly("a", "b");
    assertThat(TomlLists.optional(toml(), "absent")).isEmpty();
    assertThat(TomlValues.stringList(toml().getArray("list"), "list")).isEqualTo(Set.of("a", "b"));
  }

  @Test
  void rejectsMissingAndWrongTypedValues() {
    assertThatThrownBy(() -> TomlValues.parse("a = "))
        .isInstanceOf(RuleConfigurationException.class);
    assertThatThrownBy(() -> TomlValues.required(toml(), "absent"))
        .isInstanceOf(RuleConfigurationException.class);
    assertThatThrownBy(() -> TomlValues.integer(toml(), "absent"))
        .isInstanceOf(RuleConfigurationException.class);
    assertThatThrownBy(() -> TomlValues.integer(toml(), "big"))
        .isInstanceOf(RuleConfigurationException.class);
    assertThatThrownBy(() -> TomlValues.integer(toml(), "negative"))
        .isInstanceOf(RuleConfigurationException.class);
    assertThatThrownBy(() -> TomlValues.strings(toml(), "absent"))
        .isInstanceOf(RuleConfigurationException.class);
    assertThatThrownBy(() -> TomlValues.strings(toml(), "numbers"))
        .isInstanceOf(RuleConfigurationException.class);
    assertThatThrownBy(() -> TomlValues.stringList(null, "list"))
        .isInstanceOf(RuleConfigurationException.class);
    assertThatThrownBy(() -> TomlLists.optional(toml(), "numbers"))
        .isInstanceOf(RuleConfigurationException.class);
  }

  @Test
  void listsOfStringsKeepOrderInTomlLists() {
    assertThat(TomlLists.optional(TomlValues.parse("l = [\"b\", \"a\"]"), "l"))
        .isEqualTo(List.of("b", "a"));
  }
}
