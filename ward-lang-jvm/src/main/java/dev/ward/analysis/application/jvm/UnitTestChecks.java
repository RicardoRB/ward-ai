package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.UnitTestAssertCountCheck;
import dev.ward.analysis.domain.rule.check.UnitTestLifecycleCheck;
import dev.ward.analysis.domain.rule.check.UnitTestMissingAnnotationCheck;
import dev.ward.analysis.domain.rule.check.UnitTestWithoutAssertCheck;
import java.util.Map;
import java.util.regex.Pattern;

/** Builds the checks of the rules that read JUnit test classes. */
final class UnitTestChecks {
  private static final String MESSAGE = "message";
  private static final String MAX = "max";
  private static final String JUNIT4_ONLY = "junit4-only";

  private UnitTestChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "unit-test-without-assert",
            toml -> new UnitTestWithoutAssertCheck(TomlValues.required(toml, MESSAGE))
        ),
        entry(
            "unit-test-assert-count",
            toml ->
                new UnitTestAssertCountCheck(
                    TomlValues.integer(toml, MAX),
                    TomlValues.required(toml, MESSAGE)
                )
        ),
        entry(
            "unit-test-missing-annotation",
            toml ->
                new UnitTestMissingAnnotationCheck(
                    Pattern.compile(TomlValues.required(toml, "class-pattern")),
                    TomlValues.required(toml, MESSAGE),
                    toml.getBoolean(JUNIT4_ONLY, () -> false)
                )
        ),
        entry(
            "unit-test-lifecycle",
            toml ->
                new UnitTestLifecycleCheck(
                    TomlValues.required(toml, "method"),
                    TomlValues.strings(toml, "annotations"),
                    TomlValues.required(toml, MESSAGE),
                    toml.getBoolean(JUNIT4_ONLY, () -> false)
                )
        )
    );
  }
}
