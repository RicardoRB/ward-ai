package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.JavaAssistedParametersCheck;
import dev.ward.analysis.domain.rule.check.JavaEnclosedRunnerCheck;
import dev.ward.analysis.domain.rule.check.JavaGuardedByCheck;
import dev.ward.analysis.domain.rule.check.JavaImmutableCheck;
import dev.ward.analysis.domain.rule.check.JavaIncompatibleModifiersCheck;
import dev.ward.analysis.domain.rule.check.JavaInlineMeCheck;
import dev.ward.analysis.domain.rule.check.JavaIterablePathCheck;
import java.util.Map;

/** Builds the checks of Error Prone rules that read annotations, modifiers and imports. */
final class ErrorProneAnnotationChecks {

  private static final String MESSAGE = "message";

  private ErrorProneAnnotationChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "assisted-parameters",
            toml -> new JavaAssistedParametersCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "enclosed-runner-tests",
            toml -> new JavaEnclosedRunnerCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "guarded-by",
            toml ->
                new JavaGuardedByCheck(
                    toml.getBoolean("skip-private", () -> false),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "errorprone-immutable",
            toml ->
                new JavaImmutableCheck(
                    TomlValues.optionalStrings(toml, "annotations"),
                    toml.getBoolean("final-only", () -> false),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "incompatible-modifiers",
            toml -> new JavaIncompatibleModifiersCheck(TomlValues.required(toml, MESSAGE))),
        entry("inline-me", toml -> new JavaInlineMeCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "iterable-path-parameter",
            toml -> new JavaIterablePathCheck(TomlValues.required(toml, MESSAGE))));
  }
}
