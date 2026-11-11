package dev.ward.analysis.application.jvm;

import static java.util.Map.entry;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckRegistry;
import dev.ward.analysis.application.rule.TomlLists;
import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.rule.check.CovariantEqualsCheck;
import dev.ward.analysis.domain.rule.check.EqualsAvoidNullCheck;
import dev.ward.analysis.domain.rule.check.EqualsHashCodeCheck;
import dev.ward.analysis.domain.rule.check.FallThroughCheck;
import dev.ward.analysis.domain.rule.check.IfLiteralCheck;
import dev.ward.analysis.domain.rule.check.IllegalCatchCheck;
import dev.ward.analysis.domain.rule.check.IllegalThrowsCheck;
import dev.ward.analysis.domain.rule.check.MissingSwitchDefaultCheck;
import dev.ward.analysis.domain.rule.check.ReturnCountCheck;
import java.util.List;
import java.util.Map;

/**
 * Builds the checks of the Checkstyle-style coding rules: switch structure, return statements,
 * equals and hashCode, and the exception types that catch and throws clauses name.
 */
final class CodingChecks {

  private static final String MESSAGE = "message";
  private static final String MAX = "max";

  private CodingChecks() {}

  /** The checks this class provides, by name. */
  static Map<String, CheckFactory> checks() {
    return CheckRegistry.union(List.of(build(), buildEquality(), buildException()));
  }

  private static Map<String, CheckFactory> build() {
    return Map.ofEntries(
        entry(
            "if-literal",
            toml ->
                new IfLiteralCheck(
                    TomlLists.optional(toml, "ignore-magic-numbers"),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "fall-through",
            toml ->
                new FallThroughCheck(
                    toml.getBoolean("check-last-group", () -> false),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "missing-switch-default",
            toml -> new MissingSwitchDefaultCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "return-count",
            toml ->
                new ReturnCountCheck(
                    TomlValues.integer(toml, MAX),
                    TomlValues.integer(toml, "max-for-void"),
                    TomlValues.required(toml, MESSAGE))));
  }

  private static Map<String, CheckFactory> buildEquality() {
    return Map.ofEntries(
        entry(
            "equals-hash-code",
            toml -> new EqualsHashCodeCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "covariant-equals",
            toml -> new CovariantEqualsCheck(TomlValues.required(toml, MESSAGE))),
        entry(
            "equals-avoid-null",
            toml ->
                new EqualsAvoidNullCheck(
                    toml.getBoolean("ignore-equals-ignore-case", () -> false),
                    TomlValues.required(toml, MESSAGE))));
  }

  private static Map<String, CheckFactory> buildException() {
    return Map.ofEntries(
        entry(
            "illegal-catch",
            toml ->
                new IllegalCatchCheck(
                    TomlValues.strings(toml, "illegal-classes"),
                    TomlValues.required(toml, MESSAGE))),
        entry(
            "illegal-throws",
            toml ->
                new IllegalThrowsCheck(
                    TomlValues.strings(toml, "illegal-classes"),
                    TomlValues.strings(toml, "ignored-method-names"),
                    toml.getBoolean("ignore-overridden-methods", () -> true),
                    TomlValues.required(toml, MESSAGE))));
  }
}
