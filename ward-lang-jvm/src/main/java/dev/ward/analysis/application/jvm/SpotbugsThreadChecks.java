package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.JavaConcurrentSequenceCheck;
import dev.ward.analysis.domain.rule.check.JavaSharedVariableCheck;
import dev.ward.analysis.domain.rule.check.JavaThreadResourceCheck;
import java.util.Locale;
import java.util.Map;

/** The checks of the SpotBugs thread-safety ports for Java, which read field access and locks. */
final class SpotbugsThreadChecks {

  private static final String MESSAGE = "message";
  private static final String MODE = "mode";

  private SpotbugsThreadChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "shared-variable",
            toml ->
                new JavaSharedVariableCheck(
                    JavaSharedVariableCheck.Mode.valueOf(upper(TomlValues.required(toml, MODE))),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "concurrent-sequence",
            toml -> new JavaConcurrentSequenceCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "thread-resource",
            toml -> new JavaThreadResourceCheck(TomlValues.required(toml, MESSAGE))));
  }

  private static String upper(final String value) {
    return value.toUpperCase(Locale.ROOT).replace('-', '_');
  }
}
