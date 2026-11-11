package dev.ward.analysis.application.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.rule.RuleConfigurationException;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RuleFileReaderTest {

  private static final String DIRECTORY = "rule-files/";

  @Test
  void readsTheMetadataOfARule() {
    final List<RuleDefinition> rules = RuleFileReader.read(DIRECTORY + "valid.toml");

    assertThat(rules).hasSize(1);
    final RuleDefinition rule = rules.get(0);
    assertThat(rule.id()).isEqualTo("ward:java/example");
    assertThat(rule.category()).isEqualTo("sizes");
    assertThat(rule.presets()).hasSize(2);
    assertThat(rule.options()).hasSize(2);
  }

  @Test
  void aMissingFileNamesItself() {
    assertThatThrownBy(() -> RuleFileReader.read(DIRECTORY + "absent.toml"))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("absent.toml");
  }

  @Test
  void readsATwoLevelCategory() {
    final List<RuleDefinition> rules = RuleFileReader.read(DIRECTORY + "subcategory.toml");

    assertThat(rules.get(0).category()).isEqualTo("sizes/metrics");
  }

  @Test
  void aThreeLevelCategoryFailsAndNamesTheValue() {
    assertThatThrownBy(() -> RuleFileReader.read(DIRECTORY + "three-level-category.toml"))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("style/imports/order");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "malformed",
        "bad-language",
        "missing-body",
        "bad-preset",
        "bad-option-key",
        "bad-option-type",
      })
  void anUnusableFileFailsAsAWholeAndNamesTheFile(final String name) {
    assertThatThrownBy(() -> RuleFileReader.read(DIRECTORY + name + ".toml"))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining(name + ".toml");
  }
}
