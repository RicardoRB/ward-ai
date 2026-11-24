package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.RuleConfigurationException;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import org.junit.jupiter.api.Test;

/** Checks the {@code min-confidence} key of a taint rule body. */
class CoreCheckProviderTaintTest {
  private static final String BODY =
      "check = \"taint\"\nspecs = [\"taint/python/sources.toml\"]\nmessage = \"m\"\n";

  @Test
  void acceptsProvenPossibleAndNoValue() {
    for (final String extra : new String[] {
        "",
        "min-confidence = \"proven\"\n",
        "min-confidence = \"possible\"\n"
    }) {
      assertThat(create(BODY + extra).taintSpec()).isPresent();
    }
  }

  @Test
  void rejectsAnUnknownConfidence() {
    assertThatThrownBy(() -> create(BODY + "min-confidence = \"low\"\n"))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("min-confidence");
  }

  private static RuleCheck create(final String body) {
    return new CoreCheckProvider().checks().get("taint").create(TomlValues.parse(body));
  }
}
