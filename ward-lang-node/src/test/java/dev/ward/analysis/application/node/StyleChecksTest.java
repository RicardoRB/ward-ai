package dev.ward.analysis.application.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.RuleConfigurationException;
import org.junit.jupiter.api.Test;
import org.tomlj.TomlParseResult;

/** Reads the options of the style checks from their rule bodies. */
class StyleChecksTest {

  private static final String MAGIC = "node-magic-number";

  @Test
  void numericIgnoreNumbersEntriesAreAccepted() {
    assertThat(create("ignore-numbers = [\"9\", \"-3.5\"]\n")).isNotNull();
  }

  @Test
  void nonNumericIgnoreNumbersEntryIsAConfigurationError() {
    assertThatThrownBy(() -> create("ignore-numbers = [\"S\"]\n"))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessage("'ignore-numbers' entry 'S' is not a number");
  }

  private static Object create(final String options) {
    final String body = options + "message = \"m\"\nconst-message = \"c\"\n";
    final TomlParseResult toml = TomlValues.parse(body);
    final CheckFactory factory = StyleChecks.checks().get(MAGIC);
    return factory.create(toml);
  }
}
