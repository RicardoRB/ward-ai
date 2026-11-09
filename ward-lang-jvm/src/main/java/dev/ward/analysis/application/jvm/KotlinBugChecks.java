package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.InvalidRangeCheck;
import dev.ward.analysis.domain.rule.check.KotlinIgnoredReturnValueCheck;
import dev.ward.analysis.domain.rule.check.KotlinMapGetNotNullCheck;
import dev.ward.analysis.domain.rule.check.KotlinRedundantSuspendCheck;
import dev.ward.analysis.domain.rule.check.KotlinReferentialEqualityCheck;
import dev.ward.analysis.domain.rule.check.UnreachableStatementCheck;
import dev.ward.analysis.domain.rule.check.UnusedUnaryOperatorCheck;
import java.util.Map;

/**
 * Builds the Kotlin checks of detekt's rule sets for potential bugs and coroutines, the ones that
 * read statements, ranges and type facts.
 */
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
            toml -> new UnusedUnaryOperatorCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-referential-equality",
            toml ->
                new KotlinReferentialEqualityCheck(
                    TomlValues.strings(toml, "forbidden-types"),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-map-get-not-null",
            toml -> new KotlinMapGetNotNullCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-ignored-return-value",
            toml ->
                new KotlinIgnoredReturnValueCheck(
                    TomlValues.strings(toml, "return-value-annotations"),
                    TomlValues.strings(toml, "ignore-return-value-annotations"),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "kotlin-redundant-suspend",
            toml -> new KotlinRedundantSuspendCheck(TomlValues.required(toml, MESSAGE))));
  }
}
