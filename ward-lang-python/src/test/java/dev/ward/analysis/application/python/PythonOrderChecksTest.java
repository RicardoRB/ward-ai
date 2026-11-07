package dev.ward.analysis.application.python;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The Python provider registers every order check, and each one builds from its table. */
final class PythonOrderChecksTest {

  private static final List<String> NAMES =
      List.of(
          "comparison-operator",
          "loop-variable",
          "except-order",
          "raise-without-from",
          "undefined-export",
          "duplicate-handler",
          "fstring-docstring",
          "unbound-local",
          "loop-closure",
          "empty-abstract-method",
          "finally-jump",
          "set-duplicates");

  @Test
  void everyOrderCheckIsRegisteredAndBuilds() {
    final Map<String, CheckFactory> checks = new PythonCheckProvider().checks();
    for (final String name : NAMES) {
      final RuleCheck check = checks.get(name).create(TomlValues.parse(tableFor(name)));
      assertThat(check).as(name).isNotNull();
    }
  }

  /** A table for the check; only the two checks with a problem key read one. */
  private static String tableFor(final String name) {
    return switch (name) {
      case "comparison-operator" -> "message = \"m\"\nproblem = \"not-in\"\n";
      case "loop-variable" -> "message = \"m\"\nproblem = \"unused\"\n";
      default -> "message = \"m\"\n";
    };
  }

  @Test
  void problemKeysSelectTheirEnumConstants() {
    final Map<String, CheckFactory> checks = new PythonCheckProvider().checks();

    assertThat(
            checks
                .get("comparison-operator")
                .create(TomlValues.parse("message = \"m\"\nproblem = \"is-literal\"\n")))
        .isNotNull();
    assertThat(
            checks
                .get("loop-variable")
                .create(TomlValues.parse("message = \"m\"\nproblem = \"shadows-import\"\n")))
        .isNotNull();
  }
}
