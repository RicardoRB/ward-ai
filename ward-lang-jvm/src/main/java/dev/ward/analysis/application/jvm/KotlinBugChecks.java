package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.InvalidRangeCheck;
import dev.ward.analysis.domain.rule.check.UnreachableStatementCheck;
import dev.ward.analysis.domain.rule.check.UnusedUnaryOperatorCheck;
import java.util.Map;

/** Builds the Kotlin checks of detekt's potential-bugs set that read statements and ranges. */
final class KotlinBugChecks {

  private static final String MESSAGE = "message";

  private KotlinBugChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return Map.ofEntries(
        entry(
            "kotlin-invalid-range",
            toml -> new InvalidRangeCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-unreachable-code",
            toml -> new UnreachableStatementCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-unused-unary-operator",
            toml -> new UnusedUnaryOperatorCheck(TomlValues.required(toml, MESSAGE))));
  }
}
